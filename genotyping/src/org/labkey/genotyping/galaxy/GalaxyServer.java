/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.genotyping.galaxy;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.genotyping.GenotypingManager;
import org.labkey.genotyping.GenotypingFolderSettings;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Aug 25, 2010
 * Time: 3:36:36 PM
 */

// Provides ability to query and modify a Galaxy Server via the HTTP Galaxy API.  All API calls require a Galaxy-generated
// web API key (see User -> Preferences -> Manage your information).
public class GalaxyServer
{
    private final String _baseUrl;
    private final String _key;
    private final HttpClient _client;


    public GalaxyServer(String serverUrl, String key)
    {
        _baseUrl = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + "api/libraries";
        _key = key;
        _client = new HttpClient();

    }


    // Throws NotFoundException if either galaxy URL (admin responsibility) or web API key (user responsibility) isn't configured.
    public static GalaxyServer get(Container c, User user)
    {
        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);

        if (null == settings.getGalaxyURL())
        {
            String advice = c.hasPermission(user, AdminPermission.class) ? "Please configure the Galaxy settings using the \"admin\" link" : "An administrator must configure the Galaxy settings";
            throw new NotFoundException("Galaxy server URL is not configured. " + advice);
        }

        GalaxyUserSettings userSettings = GenotypingManager.get().getUserSettings(c, user);

        if (null == userSettings.getGalaxyKey())
            throw new NotFoundException("You must first configure a Galaxy web API key using the \"my settings\" link");

        return new GalaxyServer(settings.getGalaxyURL(), userSettings.getGalaxyKey());
    }


    public List<DataLibrary> getDataLibraries() throws IOException
    {
        return getDataLibraries(get(""));
    }


    // Parse one or more libraries from a JSON array
    private List<DataLibrary> getDataLibraries(String body)
    {
        JSONArray array = new JSONArray(body);
        List<DataLibrary> list = new LinkedList<DataLibrary>();

        for (int i = 0; i < array.length(); i++)
            list.add(new DataLibrary((JSONObject)array.get(i)));

        return list;
    }


    private String get(String relativeUrl) throws IOException
    {
        GetMethod get = new GetMethod(makeUrl(relativeUrl));
        int responseCode = _client.executeMethod(get);
        String response = get.getResponseBodyAsString();

        if (HttpStatus.SC_OK != responseCode)
            throw new IOException("HTTP Get Failed: " + response);

        return response;
    }


    private String makeUrl(String relativeUrl)
    {
        return (relativeUrl.isEmpty() ? _baseUrl : _baseUrl + "/" + relativeUrl) + "?key=" + _key;
    }


    public DataLibrary createLibrary(String name, @Nullable String description, @Nullable String synopsis) throws IOException
    {
        JSONWriter writer = new JSONStringer();
        writer.object();
        writer.key("name").value(name);

        if (null != description)
            writer.key("description").value(description);

        if (null != synopsis)
            writer.key("synopsis").value(synopsis);

        writer.endObject();

        String response = post("", writer.toString());

        List<DataLibrary> list = getDataLibraries(response);

        assert 1 == list.size();

        return list.get(0);
    }


    private String post(String relativeUrl, String body) throws IOException
    {
        PostMethod post = new PostMethod(makeUrl(relativeUrl));
        post.setRequestEntity(new StringRequestEntity(body, "application/json", null));
        post.setRequestHeader("Content-Type", "application/json");

        HttpClient client = new HttpClient();
        int responseCode = client.executeMethod(post);

        if (HttpStatus.SC_OK != responseCode)
            throw new IOException("HTTP Post Failed");

        return post.getResponseBodyAsString();
    }


    public class Item
    {
        private final ItemType _type;
        private final String _url;
        private final String _name;
        private final String _id;
        private final @Nullable Item _parent;

        private Item(@NotNull ItemType type, @Nullable Item parent, JSONObject json)
        {
            this(type, parent, json.getString("url"), json.getString("name"), json.getString("id"));
        }

        private Item(@NotNull ItemType type, @Nullable Item parent, String url, String name, String id)
        {
            _type = type;
            _parent = parent;
            _url = url;
            _name = name;
            _id = id;
        }

        public ItemType getType()
        {
            return _type;
        }

        public @Nullable Item getParent()
        {
            return _parent;
        }

        public String getUrl()
        {
            return _url;
        }

        public String getName()
        {
            return _name;
        }

        public String getId()
        {
            return _id;
        }
    }

    public class DataLibrary extends Item
    {
        private DataLibrary(JSONObject library)
        {
            super(ItemType.DataLibrary, null, library);
        }

        public Folder getRootFolder() throws IOException
        {
            List<LibraryItem> children = getChildren();

            for (LibraryItem child : children)
            {
                if (this.equals(child.getParent()) && "/".equals(child.getName()))
                    return (Folder)child;
            }

            throw new IllegalStateException("No root folder found for data library " + getName());
        }

        public List<LibraryItem> getChildren() throws IOException
        {
            String body = get(getId() + "/contents");

            JSONArray array = new JSONArray(body);
            List<LibraryItem> list = new LinkedList<LibraryItem>();

            for (int i = 0; i < array.length(); i++)
                list.add(getLibraryItem(this, (JSONObject)array.get(i)));

            return list;
        }
    }

    public enum ItemType {DataLibrary, LibraryFolder, LibraryFile}

    private LibraryItem getLibraryItem(Item parent, JSONObject json)
    {
        String type = json.getString("type");

        if ("folder".equals(type))
            return new Folder(parent, json);
        else
            return new File(parent, json);
    }

    public abstract class LibraryItem extends Item
    {
        private LibraryItem(@NotNull ItemType type, @Nullable Item parent, JSONObject json)
        {
            super(type, parent, json);
        }

        public Folder createFolder(String name, String description) throws IOException
        {
            JSONWriter writer = new JSONStringer();
            writer.object();
            writer.key("folder_id").value(getId());
            writer.key("name").value(name);
            writer.key("create_type").value("folder");

            if (null != description)
                writer.key("description").value(description);

            writer.endObject();

            String json = post(getId() + "/contents", writer.toString());
            JSONArray array = new JSONArray(json);

            return new Folder(this, (JSONObject)array.get(0)); 
        }
    }

    public class Folder extends LibraryItem
    {
        private Folder(@Nullable Item parent, JSONObject json)
        {
            super(ItemType.LibraryFolder, parent, json);
        }

        // TODO: implement link vs. copy
        public List<File> uploadFromImportDirectory(String serverPath, String fileType, @Nullable String dbKey, boolean linkData) throws IOException
        {
            JSONWriter writer = new JSONStringer();
            writer.object();
            writer.key("folder_id").value(getId());
            writer.key("server_dir").value(serverPath);
            writer.key("file_type").value(fileType);
            writer.key("dbkey").value(null != dbKey ? dbKey : "?");
            writer.key("upload_option").value("upload_directory");
            writer.key("create_type").value("file");
            writer.endObject();

            String json = post(getId() + "/contents", writer.toString());

            // Parse one or more folders from a JSON array
            JSONArray array = new JSONArray(json);
            List<File> list = new LinkedList<File>();

            for (int i = 0; i < array.length(); i++)
                list.add(new File(this, (JSONObject)array.get(i)));

            return list;
        }
    }

    public class File extends LibraryItem
    {
        private File(@Nullable Item parent, JSONObject json)
        {
            super(ItemType.LibraryFile, parent, json);
        }

        public Map<String, Object> getProperties() throws IOException
        {
            String body = get(getParent().getId() + "/contents/" + getId());
            return new JSONObject(body);
        }
    }
}
