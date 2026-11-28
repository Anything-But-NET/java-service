package com.anything_but_dotnet.copilot.config;

import com.anything_but_dotnet.copilot.model.ChatChannel;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class StringToChatChannelConverter implements Converter<String, ChatChannel> {

    @Override
    public ChatChannel convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return ChatChannel.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}