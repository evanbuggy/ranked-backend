package com.shared.entities;
//authored by Liam Kelly, 22346317

import com.fasterxml.jackson.annotation.JsonCreator;

public class Player {
    String tag;
    int globalStartGgId;
    @JsonCreator
    public Player(String tag, int globalStartGgId){
        this.tag = tag;
        this.globalStartGgId = globalStartGgId;
    }
    public String getTag(){
        return this.tag;
    }
    public int getGlobalStartGgId(){
        return this.globalStartGgId;
    }
}
