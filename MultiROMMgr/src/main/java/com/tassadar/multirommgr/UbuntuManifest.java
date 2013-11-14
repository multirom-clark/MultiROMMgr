/*
 * This file is part of MultiROM Manager.
 *
 * MultiROM Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MultiROM Manager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MultiROM Manager. If not, see <http://www.gnu.org/licenses/>.
 */

package com.tassadar.multirommgr;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class UbuntuManifest {
    public static final String BASE_URL = "http://system-image.ubuntu.com";
    public static final String CHANNELS_URL = BASE_URL + "/channels.json";

    public boolean downloadAndParse(Device dev) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        try {
            if(!Utils.downloadFile(CHANNELS_URL, out, null) || out.size() == 0)
                return false;
        } catch(IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                out.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        try {
            Object rawObject = new JSONTokener(out.toString()).nextValue();
            if(!(rawObject instanceof JSONObject)){
                Log.e("UbuntuManifest", "Malformed manifest format!");
                return false;
            }

            JSONObject o = (JSONObject)rawObject;
            SharedPreferences pref = MultiROMMgrApplication.getPreferences();

            Iterator itr = o.keys();
            while(itr.hasNext()) {
                String name = (String)itr.next();
                JSONObject c = o.getJSONObject(name);

                // Skip hidden channels
                if(c.optBoolean("hidden", false) &&
                   !pref.getBoolean(SettingsActivity.UTOUCH_SHOW_HIDDEN, false)) {
                    continue;
                }

                m_channels.put(name, new UbuntuChannel(name, c));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        // Remove duplicate channels (they have "alias" set) and
        // channels without the device we're currently running on
        Iterator<Map.Entry<String,UbuntuChannel>> itr = m_channels.entrySet().iterator();
        while(itr.hasNext()) {
            UbuntuChannel c = itr.next().getValue();

            if(!c.hasDevice(dev.getName())) {
                itr.remove();
                continue;
            }

            if(c.getAlias() != null) {
                UbuntuChannel orig = m_channels.get(c.getAlias());
                if(orig != null) {
                    orig.addDuplicate(c.getRawName());
                    itr.remove();
                    continue;
                }
            }

            try {
                if(!c.loadDeviceImages(dev.getName()))
                    return false;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            Log.d("UbuntuManifest", "Got channel: " + c.getDisplayName());
        }
        return true;
    }

    public Map<String,UbuntuChannel> getChannels() { return m_channels; }

    private TreeMap<String, UbuntuChannel> m_channels = new TreeMap<String, UbuntuChannel>();
}
