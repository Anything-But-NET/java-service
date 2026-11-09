package com.anything_but_dotnet.copilot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ChatChannel {
    @JsonProperty("slack")
    SLACK,
    @JsonProperty("teams")
    TEAMS
}
