package practica1;

import Environment.Environment;
import agents.DEST;
import agents.LARVAFirstAgent;
import ai.Choice;
import ai.DecisionSet;
import ai.Plan;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import tools.emojis;
import world.Perceptor;


public class ITT extends LARVAFirstAgent {

    // The execution on any agent might be seen as a finite state automaton whose states are these
    enum Status {
        START, 
        CHECKIN, 
        OPENPROBLEM, 
        JOINSESSION, 
        SOLVEPROBLEM, 
        CLOSEPROBLEM, 
        CHECKOUT, 
        EXIT
    }
    Status myStatus;    // The current state
    String service = "PMANAGER", 
            problem = "",
            problemManager = "", 
            sessionManager, 
            content, 
            sessionKey;
    ACLMessage open, session; // Backup of relevant messages
    String[] contentTokens; // To parse the content
    protected String action = "", preplan = "", whichWall, nextWhichwall;
    protected String[] problems;
    protected int indexplan = 0, myEnergy = 0, indexSensor = 0;
    protected double distance, nextdistance; // Which was the distance to the goal in that moment
    protected Point3D point, nextPoint; // Which was the GPS position of the agente in that moment?
    protected boolean showPerceptions = false, useAlias = false;
    Plan behaviour = null;
    Environment Ei, Ef;
    Choice a;

    // This is the firs method executed by any agent, right before its creation
    @Override
    public void setup() {
//        this.enableDeepLARVAMonitoring();
        super.setup();
        logger.onTabular();
        myStatus = Status.START;
        this.setupEnvironment();

        // Acciones disponibles
        A = new DecisionSet();
        A.addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
        
        problems = new String[] {
            "SandboxTesting",
            "FlatNorth",
            "FlatNorthWest",
            "FlatSouth",
            "Bumpy0",
            "Bumpy1",
            "Bumpy2",
            "Bumpy3",
            "Bumpy4",
            "Halfmoon1",
            "Halfmoon3",
            "Dagobah.Apr1",
            "Dagobah.Apr2",
            "Dagobah.Not1",
            "Dagobah.Not2",
            "Endor.Sob1",
            "Endor.Sob2",
            "Endor.Hon1",
            "Endor.Hon2",
        };
    }

    // Main execution body. It executes continuously until doExit(). after which it executes takeDown()
    @Override
    public void Execute() {
        Info("\n\n Status: " + myStatus.name());
        switch (myStatus) {
            case START:
                myStatus = Status.CHECKIN;
                break;
            case CHECKIN: // The execution of a state (as a method) returns the next state
                myStatus = MyCheckin();
                break;
            case OPENPROBLEM:
                myStatus = MyOpenProblem();
                break;
            case JOINSESSION:
                myStatus = MyJoinSession();
                break;
            case SOLVEPROBLEM:
                myStatus = MySolveProblem();
                break;
            case CLOSEPROBLEM:
                myStatus = MyCloseProblem();
                break;
            case CHECKOUT:
                myStatus = MyCheckout();
                break;
            case EXIT:
            default:
                doExit();
                break;
        }
    }

    @Override
    public void takeDown() {
        Info("Taking down...");
        // Save the Sequence Diagram
//        this.saveSequenceDiagram("./" + getLocalName() + ".seqd");
        super.takeDown();
    }

    // It loads the passport selected in the GUI and send it to the Identity manager
    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        //this.loadMyPassport("config/<ANATOLI_GRISHENKO>.passport");
        // If checkin works, then continue, else exit
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.OPENPROBLEM;
    }

    // Says good by to the Identity Manager and leaves LARVA
    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }

    public Status MyOpenProblem() {
        // Look i the DF who is in charge of service PMANAGER
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        
        problem = this.inputSelect("Please select problem to solve", problems, "");
        if (problem == null)
            return Status.CHECKOUT;

        // Send it a message to open a problem instance
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem);
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);

        // There will be arriving two messages, one coming from the
        // Problem Manager and the other from the brand new Session Manager
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return Status.JOINSESSION;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }

    // Just register in the DF as a terrestrial agent AT_ST
    public Status MyJoinSession() {
        Info("Querying CITIES");
        outbox = new ACLMessage();
        outbox.setSender(this.getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setContent("Query CITIES session " + sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        String[] cities = E.getCityList();
        String city = inputSelect("Please select the city", cities, "");
        
        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();
        outbox.setContent("Request join session " + sessionKey + "in " + city);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Confirm")) {
            Error("Could not join session " + sessionKey + " due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        this.openRemote();
        this.MyReadPerceptions();
        this.doPrepareNPC(1, DEST.class);
//        Info(this.easyPrintPerceptions());
//        // Mark this GPS positoin as our destination
//        return this.myAssistedNavigation(37, 13);

        outbox = session.createReply();
        outbox.setContent("Query missions session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        String mission = chooseMission();
        if (mission == null)
            return Status.CLOSEPROBLEM;
        
        E.setCurrentMission(mission);
        
        return Status.SOLVEPROBLEM;
    }

    public Status MySolveProblem() {
        if (G(E)) {
            Info("The problem is over");
            Message("The problem " + problem + " has been solved");
            return Status.CLOSEPROBLEM;
        }
        behaviour = AgPlan(E, A);
        // If no choice is selected is due to a confusion of the AgPlan( ) function, then stop
        if (behaviour == null || behaviour.isEmpty()) {
            Alert("Found no plan to execute");
            return Status.CLOSEPROBLEM;
        } else {// Execute
            Info("Found plan: " + behaviour.toString());
            while (!behaviour.isEmpty()) {
                a = behaviour.get(0);
                behaviour.remove(0);
                Info("Excuting " + a);
                this.MyExecuteAction(a.getName());
                // If we have made a mistake in the selection of the best choice a to execute,
                // this might compromise the integrity of the agent. Check that it has noe died!
                if (!Ve(E)) {
                    this.Error("The agent is not alive: " + E.getStatus());
                    return Status.CLOSEPROBLEM;
                }
            }
            // Afterwards, read the sensors to reubicate the new state
            this.MyReadPerceptions();
            return Status.SOLVEPROBLEM;
        }
    }

    protected boolean MyExecuteAction(String action) {
        Info("Executing action " + action);
        outbox = session.createReply();
        // Remember to include sessionID in all communications
        outbox.setContent("Request execute " + action + " session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Inform")) {
            Error("Unable to execute action " + action + " due to " + session.getContent());
            return false;
        }
        return true;
    }

    @Override
    protected Choice Ag(Environment E, DecisionSet A) {
        if (G(E)) {
            return null;
        } else if (A.isEmpty()) {
            return null;
        } else {
            A = Prioritize(E, A);
            whichWall = nextWhichwall;
            distance = nextdistance;
            point = nextPoint;
            return A.BestChoice();
        }
    }

    protected Plan AgPlan(Environment E, DecisionSet A) {
        Ei = E.clone();
        Plan p = new Plan();
        for (int i = 0; i < Ei.getRange() / 2 - 2; i++) {
            Ei.cache();
            if (!Ve(Ei)) {
                return null;
            } else if (G(Ei)) {
                return p;
            } else {
                a = Ag(Ei, A);
                if (a != null) {
                    p.add(a);
                    Ef = S(Ei, a);
                    Ei = Ef;
                } else {
                    return null;
                }
            }
        }
        return p;
    }

    public boolean isFreeFront() {
        int slopeFront, visualFront, visualHere;

        visualFront = E.getPolarVisual()[2][1];
        slopeFront = E.getPolarLidar()[2][1];

        return slopeFront >= -E.getMaxslope() && slopeFront <= E.getMaxslope()
                && visualFront >= E.getMinlevel()
                && visualFront <= E.getMaxlevel();
    }

    public double goAhead(Environment E, Choice a) {
        if (a.getName().equals("MOVE")) {
            return U(S(E, a));
        } else if (a.getName().equals("LEFT") || a.getName().equals("RIGHT")) {
            return U(S(E, a), new Choice("MOVE"));
        }
        return Choice.MAX_UTILITY;
    }

    public double goAvoid(Environment E, Choice a) {
        // By default, we avoid something in front of us by turing always right
        if (a.getName().equals("RIGHT")) {
            nextWhichwall = "LEFT";
            nextdistance = E.getDistance();
            nextPoint = E.getGPS();
            return Choice.ANY_VALUE; // I give the choice "RIGHT" a positive value, any
        }
        return Choice.MAX_UTILITY; // and the others just a penalty with the max value, so that the decisoin is clear, isn't it?
    }

    public double goFollowWallLeft(Environment E, Choice a) {
        if (E.isFreeFrontLeft()) {
            return goTurnOnWallLeft(E, a);
        } else if (E.isTargetFrontRight()
                && E.isFreeFrontRight()
                && E.getDistance() < point.planeDistanceTo(E.getTarget())) {
            return goStopWallLeft(E, a);
        } else if (E.isFreeFront()) {
            return goKeepOnWall(E, a);
        } else {
            return goRevolveWallLeft(E, a);
        }
    }

    public double goKeepOnWall(Environment E, Choice a) {
        if (a.getName().equals("MOVE")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    public double goTurnOnWallLeft(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;

    }

    public double goRevolveWallLeft(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    public double goStopWallLeft(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            this.resetAutoNAV();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    @Override
    protected double U(Environment E, Choice a) {
        if (whichWall.equals("LEFT")) {
            return goFollowWallLeft(E, a);
        } else if (!E.isFreeFront()) {
            return goAvoid(E, a);
        } else {
            return goAhead(E, a);
        }
    }

    // Read perceptions and send them directly to the Environment instance,
    // so we can query any items of sensors and added-value information
    protected boolean MyReadPerceptions() {
        Info("Reading perceptions");
        outbox = session.createReply();
        outbox.setContent("Query sensors session " + sessionKey);
        this.LARVAsend(outbox);
        this.myEnergy++;
        session = this.LARVAblockingReceive();
        if (session.getContent().startsWith("Failure")) {
            Error("Unable to read perceptions due to " + session.getContent());
            return false;
        }
        this.getEnvironment().setExternalPerceptions(session.getContent());
        Info(this.easyPrintPerceptions());
        return true;
    }

    // Just mark an X Y position as our next target. No more
    protected Status myAssistedNavigation(int goalx, int goaly) {
        Info("Requesting course to " + goalx + " " + goaly);
        outbox = session.createReply();
        outbox.setContent("Request course to " + goalx + " " + goaly + " Session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        return Status.CHECKIN.SOLVEPROBLEM;
    }

    // Every time a stop surrounding, I delete the previous memorized values 
    public void resetAutoNAV() {
        nextWhichwall = whichWall = "NONE";
        nextdistance = distance = Choice.MAX_UTILITY;
        nextPoint = point = null;
    }

    public Status MyCloseProblem() {
        Info("Plan = " + preplan);
        // Closing the problem by replying to the backup message
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem " + problem + ", session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }

    // A new method just to show the information of sensors in console
    public String easyPrintPerceptions() {
        String res;
        int matrix[][];
        if (!logger.isEcho()) {
            return "";
        }
        if (getEnvironment() == null) {
            Error("Environment is unacessible, please setupEnvironment() first");
            return "";
        }
        if (!showPerceptions) {
            return "";
        }
        res = "\n\nReading of sensors\n";
        if (getEnvironment().getName() == null) {
            res += emojis.WARNING + " UNKNOWN AGENT";
            return res;
        } else {
            res += emojis.ROBOT + " " + getEnvironment().getName();
        }
        res += "\n";
        res += String.format("%10s: %05d W %05d W %05d W\n", "ENERGY",
                getEnvironment().getEnergy(), getEnvironment().getEnergyburnt(), myEnergy);
        res += String.format("%10s: %15s\n", "POSITION", getEnvironment().getGPS().toString());
        res += "PAYLOAD " + getEnvironment().getPayload() + " m" + "\n";
        res += String.format("%10s: %05d m\n", "X", getEnvironment().getGPS().getXInt())
                + String.format("%10s: %05d m\n", "Y", getEnvironment().getGPS().getYInt())
                + String.format("%10s: %05d m\n", "Z", getEnvironment().getGPS().getZInt())
                + String.format("%10s: %05d m\n", "MAXLEVEL", getEnvironment().getMaxlevel())
                + String.format("%10s: %05d m\n", "MAXSLOPE", getEnvironment().getMaxslope());
        res += String.format("%10s: %05d m\n", "GROUND", getEnvironment().getGround());
        res += String.format("%10s: %05d º\n", "COMPASS", getEnvironment().getCompass());
        if (getEnvironment().getTarget() == null) {
            res += String.format("%10s: " + "!", "TARGET");
        } else {
            res += String.format("%10s: %05.2f m\n", "DISTANCE", getEnvironment().getDistance());
            res += String.format("%10s: %05.2f º\n", "ABS ALPHA", getEnvironment().getAngular());
            res += String.format("%10s: %05.2f º\n", "REL ALPHA", getEnvironment().getRelativeAngular());
        }
        res += "\nVISUAL ABSOLUTE\n";
        matrix = getEnvironment().getAbsoluteVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "[  ]-";
            }
        }
        res += "\nVISUAL RELATIVE\n";
        matrix = getEnvironment().getRelativeVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "[  ]-";
            }
        }
        res += "\nVISUAL POLAR\n";
        matrix = getEnvironment().getPolarVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        res += "\n";
        res += "LIDAR RELATIVE\n";
        matrix = getEnvironment().getRelativeLidar();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "-^^-";
            }
        }
        res += "\n";
        return res
                + "\n" + this.Prioritize(E, A).toString()
                + "\nWall:\n" + whichWall + "\n";
    }

    public String easyPrintPerceptions(Environment E, DecisionSet A) {
        String res;
        int matrix[][];

        if (getEnvironment() == null) {
            Error("Environment is unacessible, please setupEnvironment() first");
            return "";
        }
        res = "\n\nReading of sensors\n";
        if (E.getName() == null) {
            res += emojis.WARNING + " UNKNOWN AGENT";
            return res;
        } else {
            res += emojis.ROBOT + " " + E.getName();
        }
        res += "\n";
        res += String.format("%10s: %05d W\n", "ENERGY", E.getEnergy());
        res += String.format("%10s: %15s\n", "POSITION", E.getGPS().toString());
//        res += "PAYLOAD "+E.getPayload()+" m"+"\n";
        res += String.format("%10s: %05d m\n", "X", E.getGPS().getXInt())
                + String.format("%10s: %05d m\n", "Y", E.getGPS().getYInt())
                + String.format("%10s: %05d m\n", "Z", E.getGPS().getZInt())
                + String.format("%10s: %05d m\n", "MAXLEVEL", E.getMaxlevel())
                + String.format("%10s: %05d m\n", "MAXSLOPE", E.getMaxslope());
        res += String.format("%10s: %05d m\n", "GROUND", E.getGround());
        res += String.format("%10s: %05d º\n", "COMPASS", E.getCompass());
        if (E.getTarget() == null) {
            res += String.format("%10s: " + "!", "TARGET");
        } else {
            res += String.format("%10s: %05.2f m\n", "DISTANCE", E.getDistance());
            res += String.format("%10s: %05.2f º\n", "ABS ALPHA", E.getAngular());
            res += String.format("%10s: %05.2f º\n", "REL ALPHA", E.getRelativeAngular());
        }
        res += "\nVISUAL RELATIVE\n";
        matrix = E.getRelativeVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "[  ]-";
            }
        }
        res += "LIDAR RELATIVE\n";
        matrix = E.getRelativeLidar();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "-^^-";
            }
        }
        res += "\n";
        res += "Decision Set: " + A.toString() + "\n";
        return res;
    }

    protected String printValue(int v) {
        if (v == Perceptor.NULLREAD) {
            return "XXX ";
        } else {
            return String.format("%03d ", v);
        }
    }

    protected String printValue(double v) {
        if (v == Perceptor.NULLREAD) {
            return "XXX ";
        } else {
            return String.format("%05.2f ", v);
        }
    }

}