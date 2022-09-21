package helloworld;

import jade.core.Agent;

public class AgentJADE extends Agent {
    
    @Override
    public void setup(){
        System.out.print("Hello my name is " + this.getLocalName());
        doDelete();
    }
}
