package com.shared.entities;

public class Player {
    String tag;
    int globalStartGgId;
    public Player(String tag, int globalStartGgId){
        this.tag = tag;
        this.globalStartGgId = globalStartGgId;
    }
    String getTag(){
        return this.tag;
    }
    int getGlobalStartGgId(){
        return this.globalStartGgId;
    }
}
