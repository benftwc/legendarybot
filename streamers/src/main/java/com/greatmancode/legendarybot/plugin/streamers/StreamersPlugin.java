/*
 * MIT License
 *
 * Copyright (c) Copyright (c) 2017-2017, Greatmancode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.greatmancode.legendarybot.plugin.streamers;

import com.greatmancode.legendarybot.api.plugin.LegendaryBotPlugin;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import net.dv8tion.jda.core.entities.Guild;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.pf4j.PluginWrapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

/**
 * Streamer plugin - Currently allows a guild to have a list of streamers registered.
 */
public class StreamersPlugin extends LegendaryBotPlugin {

    /**
     * The HTTP client to do web requests.
     */
    private OkHttpClient client = new OkHttpClient();

    /**
     * The properties file containing all the settings
     */
    private Properties props;

    /**
     * The setting key for status
     */
    public static final String STATUS_KEY = "status";

    /**
     * The setting key for Game
     */
    public static final String GAME_KEY = "game";

    /**
     * The setting key for the configuration in the database.
     */
    public static final String CONFIG_KEY = "streamersPlugin";

    public StreamersPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        //Load the configuration
        props = new Properties();
        try {
            props.load(new FileInputStream("app.properties"));
        } catch (java.io.IOException e) {
            e.printStackTrace();
            getBot().getStacktraceHandler().sendStacktrace(e);
        }
        getBot().getCommandHandler().addCommand("streamers", new StreamersCommand(this), "General Commands");
        log.info("Command !streamers loaded!");
        getBot().getCommandHandler().addCommand("addstreamer", new AddStreamerCommand(this), "Streamers Admin Commands");
        log.info("Command !addstreamer loaded!");
        getBot().getCommandHandler().addCommand("removestreamer", new RemoveStreamerCommand(this), "Streamers Admin Commands");
        log.info("Command !removestreamer unloaded!");

        log.info("Converting old streamers config");
        getBot().getJDA().forEach(jda -> jda.getGuilds().forEach(guild -> {
            String setting = getBot().getGuildSettings(guild).getSetting(CONFIG_KEY);
            if (setting != null) {
                log.info("Converting " + guild.getId() + ":" + guild.getName() + " streamers config.");
                for (String streamer : setting.split(";")) {
                    String[] streamerValues = streamer.split(",");
                    if (streamerValues.length == 2) {
                        try {
                            StreamPlatform platform = StreamPlatform.valueOf(streamerValues[1]);
                            addStreamer(guild,streamerValues[0],platform);
                        } catch(IllegalArgumentException ignored) {
                            log.warn("Invalid config for streamer " + Arrays.toString(streamerValues));
                        }
                    }
                }
                getBot().getGuildSettings(guild).unsetSetting(CONFIG_KEY);
                log.info("Done converting " + guild.getId() + ":" + guild.getName() + " streamers config.");
            }
        }));
    }

    @Override
    public void stop() {
        getBot().getCommandHandler().removeCommand("streamers");
        log.info("Command !streamers unloaded!");
        getBot().getCommandHandler().removeCommand("addstreamer");
        log.info("Command !addstreamer unloaded!");
        getBot().getCommandHandler().removeCommand("removestreamer");
        log.info("Command !removestreamer unloaded!");
    }

    /**
     * Checks if a username is streaming on a specific platform
     * @param username The Username of the user we want to check
     * @param platform The platform we want to check if the user is online on.
     * @return If the user is streaming, a Map containing {@link #STATUS_KEY} giving the current status of the user, {@link #GAME_KEY} containing the game the user is playing. Else a empty map.
     */
    public Map<String, String> isStreaming(String username, StreamPlatform platform) {
        Map<String, String> map = new HashMap<>();
        JSONParser parser = new JSONParser();
        switch (platform) {
            case TWITCH:
                Request request = new Request.Builder()
                        .url("https://api.twitch.tv/kraken/streams/"+username)
                        .addHeader("Client-ID", props.getProperty("twitch.key"))
                        .build();
                try {
                    String result = client.newCall(request).execute().body().string();

                    JSONObject json = (JSONObject) parser.parse(result);
                    JSONObject stream = (JSONObject) json.get("stream");
                    if (stream != null) {
                        map.put(STATUS_KEY, (String) ((JSONObject)stream.get("channel")).get("status"));
                        map.put(GAME_KEY, (String) stream.get("game"));
                        map.put("created_at", (String) stream.get("created_at"));
                    }
                } catch (ParseException | IOException e) {
                    e.printStackTrace();
                    getBot().getStacktraceHandler().sendStacktrace(e, "username:" + username, "platform:" + platform);
                }

                break;
            case MIXER:
                request = new Request.Builder().url("https://mixer.com/api/v1/channels/" + username).build();
                try {
                    String result = client.newCall(request).execute().body().string();
                    JSONObject json = (JSONObject) parser.parse(result);
                    if (json.containsKey("online") && (boolean)json.get("online")) {
                        JSONObject stream = (JSONObject) json.get("type");
                        map.put(STATUS_KEY, (String) json.get("name"));
                        map.put(GAME_KEY, (String) stream.get("name"));
                    }
                } catch (ParseException | IOException e) {
                    e.printStackTrace();
                    getBot().getStacktraceHandler().sendStacktrace(e, "username:" + username, "platform:" + platform);
                }
                break;
            default:
                break;
        }
        return map;
    }

    /**
     * Checks if a streamer exist on a platform
     * @param username The username to check
     * @param platform The platform to check on
     * @return True if the streamer exist, else false.
     */
    public boolean streamerExist(String username, StreamPlatform platform) {
        boolean result = false;
        switch (platform) {
            case TWITCH:
                Request request = new Request.Builder()
                        .url("https://api.twitch.tv/kraken/channels/"+username)
                        .addHeader("Client-ID", props.getProperty("twitch.key"))
                        .build();
                try {
                    result = client.newCall(request).execute().body().string() != null;
                } catch (IOException e) {
                    e.printStackTrace();
                    getBot().getStacktraceHandler().sendStacktrace(e, "username:" + username, "platform:" + platform);
                }
                break;
            case MIXER:
                request = new Request.Builder()
                        .url("https://mixer.com/api/v1/channels/" + username)
                        .build();
                try {
                    result = client.newCall(request).execute().body().string() != null;
                } catch (IOException e) {
                    e.printStackTrace();
                    getBot().getStacktraceHandler().sendStacktrace(e, "username:" + username, "platform:" + platform);
                }
                break;
            default:
                break;

        }
        return result;
    }

    /**
     * Add a streamer to the guild's settings.
     * @param guild The Guild to add the streamer to
     * @param username The username to add
     * @param platform The platform the user is streaming on.
     */
    public void addStreamer(Guild guild, String username, StreamPlatform platform) {
        MongoCollection<Document> collection = getBot().getMongoDatabase().getCollection("guild");
        collection.updateOne(eq("guild_id", guild.getId()),addToSet("streamers." + platform.toString(), username), new UpdateOptions().upsert(true));
    }

    /**
     * Remove a streamer from the database.
     * @param guild The Guild to remove the streamer from.
     * @param username The username to remove from the list
     * @param platform The platform the user is streaming from.
     */
    public void removeStreamer(Guild guild, String username, StreamPlatform platform) {
        MongoCollection<Document> collection = getBot().getMongoDatabase().getCollection("guild");
        collection.updateOne(eq("guild_id", guild.getId()),pull("streamers." +platform.toString(), username));
    }
}
