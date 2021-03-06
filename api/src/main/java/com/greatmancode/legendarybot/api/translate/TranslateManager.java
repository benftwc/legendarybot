package com.greatmancode.legendarybot.api.translate;

import com.greatmancode.legendarybot.api.LegendaryBot;
import net.dv8tion.jda.core.entities.Guild;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TranslateManager {

    private Map<String,Properties> languageMap = new HashMap<>();
    private LegendaryBot bot;

    public TranslateManager(LegendaryBot bot) throws IOException {
        load();
        this.bot = bot;
    }

    private void load() throws IOException {
        File folder = new File("languages");
        Path path = Paths.get("languages");
        Files.newDirectoryStream(path).forEach(file -> {
            Properties properties = new Properties();
            try {
                properties.load(Files.newInputStream(file));
                languageMap.put(file.getName(file.getNameCount() - 1).toString().split("\\.")[0], properties);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public String translate(Guild guild, String key, String... values) {
        String language = bot.getGuildSettings(guild).getSetting("LANGUAGE");
        if (language != null) {
            return String.format(languageMap.get(language).getProperty(key),values);
        } else {
            return String.format(languageMap.get("en").getProperty(key),values);
        }

    }

    public Set<String> getLanguages() {
        return languageMap.keySet();
    }

    public void reload() throws IOException {
        load();
    }
}
