package helloworld;

import appboot.JADEBoot;
import appboot.LARVABoot;

public class HelloWorld {
    
    public static void bootJADE() {
        JADEBoot boot = new JADEBoot();
        boot.Boot("isg2.ugr.es", 1099);
        boot.launchAgent("Smith", AgentJADE.class);
        boot.WaitToShutDown();
    }
    
    public static void bootLARVA() {
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es", 1099);
        boot.launchAgent("Smith", AgentLARVA.class);
        boot.WaitToShutDown();
    }
    
    public static void bootLARVAFull() {
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es", 1099);
        boot.launchAgent("Smith", AgentLARVAFull.class);
        boot.WaitToShutDown();
    }

    public static void main(String[] args) {
        // bootJADE();
        // bootLARVA();
        bootLARVAFull();
    }
    
}
