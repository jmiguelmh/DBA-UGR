package practica1;

import appboot.LARVABoot;
import static crypto.Keygen.getHexaKey;

public class Main {
    public static void main(String[] args) {
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es", 1099);
        boot.loadAgent("Agente Practica 1-"+getHexaKey(4), AgenteComun.class);
        boot.WaitToShutDown();
    }
}
