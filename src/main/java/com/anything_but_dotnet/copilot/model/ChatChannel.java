package com.anything_but_dotnet.copilot.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum ChatChannel {
    @JsonAlias("slack")
    SLACK,
    @JsonAlias("teams")
    TEAMS
}
