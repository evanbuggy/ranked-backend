package com.startGgIntegration.entities;

public class Entrant {
    private String name;
    private int entrantId; //An id local to an event
    private int globalId; //The global id used to find a user's profile
    public Entrant(String name, int entrantId, int globalId){
        this.name = name;
        this.entrantId = entrantId;
        this.globalId = globalId;
    }
    public String getName(){
        return this.name;
    }
    public int getEntrantId(){
        return this.entrantId;
    }
     public int getGlobalId(){
        return this.globalId;
    }
}
