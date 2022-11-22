package practica2;

import Environment.Environment;
import agents.BB1F;
import agents.DEST;
import agents.LARVAFirstAgent;
import agents.MTT;
import agents.YV;
import ai.Choice;
import ai.DecisionSet;
import ai.Plan;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import tools.emojis;
import world.Perceptor;


public class ITT extends LARVAFirstAgent {
    enum Status {
        START, 
        CHECKIN, 
        OPENPROBLEM, 
        JOINSESSION, 
        SOLVEPROBLEM,
        RECHARGE,
        CLOSEPROBLEM, 
        CHECKOUT, 
        EXIT
    }
    
    enum Type {
        MOVEIN,
        LIST,
        REPORT,
        CAPTURE
    }
    
    Status myStatus;
    String service = "PMANAGER", 
            problem = "",
            problemManager = "", 
            sessionManager, 
            content,
            dest = "",
            report = "",
            actualCity,
            sessionKey;
    ACLMessage open, session;
    String[] contentTokens;
    protected String action = "", preplan = "", whichWall, nextWhichwall;
    protected String[] problems;
    protected int indexplan = 0, myEnergy = 0, indexSensor = 0;
    protected double distance, nextdistance;
    protected Point3D point, nextPoint;
    protected boolean showPerceptions = false, useAlias = false;
    Plan behaviour = null;
    Environment Ei, Ef;
    Choice a;
    Boolean goalInitiated = false, destFounded = false, refilling = false;
    int iteratorRefillers;
    

    @Override
    public void setup() {
        this.enableDeepLARVAMonitoring();
        super.setup();
        logger.onTabular();
        myStatus = Status.START;
        this.setupEnvironment();
        
        this.deactivateSequenceDiagrams(); //Para que vaya mas rapido el bicho

        A = new DecisionSet();
        A.addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
        
        problems = new String[] {
            /*"SandboxTesting",
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
            "AlertDeathStar",*/
            "Wobani.Apr1",
            "Wobani.Not1",
            "Wobani.Sob1",
            "Wobani.Hon1"
        };
    }

    @Override
    public void Execute() {
        Info("\n\n Status: " + myStatus.name());
        switch (myStatus) {
            case START:
                myStatus = Status.CHECKIN;
                break;
            case CHECKIN:
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
            case RECHARGE:
                myStatus = MyRecharge();
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
        super.takeDown();
    }

    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.OPENPROBLEM;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }

    public Status MyOpenProblem() {
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        
        problem = this.inputSelect("Please select problem to solve", problems, "");
        if (problem == null)
            return Status.CHECKOUT;

        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem + " alias " + sessionAlias);
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);

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

    public Status MyJoinSession() {
        Info("Querying CITIES");
        outbox = session.createReply();
        outbox.setContent("Query CITIES session " + sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        String[] cities = E.getCityList();
        Info(cities[0]);
        String city = inputSelect("Please select the city", cities, "");
        
        this.doPrepareNPC(1, DEST.class);
        this.doPrepareNPC(1, BB1F.class); //Posible error
        this.doPrepareNPC(0, YV.class);
        this.doPrepareNPC(1, MTT.class);
        
        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();
        outbox.setContent("Request join session " + sessionKey + " in " + city);
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Confirm")) {
            Error("Could not join session " + sessionKey + " due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        this.openRemote();
        this.MyReadPerceptions();


        
        outbox = session.createReply();
        outbox.setContent("Query missions session " + sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        
        return SelectMission();
    }
    
    public Status SelectMission() {
        String m = chooseMission();
        if (m == null)
            return Status.CLOSEPROBLEM;
        
        getEnvironment().setCurrentMission(m);
        return Status.SOLVEPROBLEM;
    }

    public Status MySolveProblem() {
        String[] aux = getEnvironment().getCurrentGoal().split(" ");
        Type type = Type.valueOf(aux[0]);
        Status status = null;
        
        Info("Solveproblem info goal:"+aux[0]);
        
        switch(type) {
            case MOVEIN:
                if (!goalInitiated) {
                    goalInitiated = true;
                    outbox = session.createReply();
                    outbox.setContent("Request course in " + aux[1] + " session " + sessionKey);
                    outbox.setPerformative(ACLMessage.REQUEST);
                    outbox.setConversationId(sessionKey);
                    this.LARVAsend(outbox);

                    session = this.LARVAblockingReceive();
                    getEnvironment().setExternalPerceptions(session.getContent());
                }
                
                status = doMoveIn();
                
                break;
            case LIST:
                status = doQueryPeople(aux[1]);
                getEnvironment().setNextGoal();
                break;
            case REPORT:
                status = doReport();
                getEnvironment().setNextGoal();
                break;
            case CAPTURE:
                status = doCapture();
                getEnvironment().setNextGoal();
                break;
        }
        
        if (getEnvironment().getCurrentMission().isOver()){
            status = Status.CLOSEPROBLEM;
            Message("The problem " + problem + " has been solved");
        }
        if(getEnvironment().getEnergy() < 50) status = Status.RECHARGE;
        
        return status;
    }
    
    public Status MyRecharge() {
        Info("RECARGA. Nivel actual = "+Integer.toString(getEnvironment().getEnergy()));
        iteratorRefillers = 1;
        ArrayList<String> rechargers = this.DFGetAllProvidersOf("TYPE BB1F");
        ArrayList<String> rechargersList = new ArrayList<>();
        if (!rechargers.isEmpty()) {
            for (int i=0; i<rechargers.size(); i++)
                if (this.DFHasService(rechargers.get(i), sessionKey))
                    rechargersList.add(rechargers.get(i));
          
            boolean stop = false;
            int aux = 0;
            while(!stop) {
                String recharger = rechargersList.get(aux % rechargersList.size());
                aux++;
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(recharger, AID.ISLOCALNAME));
                outbox.setContent("REFILL");
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setConversationId(sessionKey);
                outbox.setProtocol("DROIDSHIP");
                this.LARVAsend(outbox);
                session = this.LARVAblockingReceive();
                if(session.getPerformative() == ACLMessage.AGREE) {
                    session = this.LARVAblockingReceive();
                    if (session.getPerformative() == ACLMessage.INFORM)
                        stop = true;
                }
            }
            
            goalInitiated = false;
            
            return Status.SOLVEPROBLEM;
        }
        
        return Status.CLOSEPROBLEM;
    }
    
    public void destFounder() {
        if (destFounded == false) {
            ArrayList<String> list;
            list = this.DFGetAllProvidersOf("TYPE DEST");
            for(int i = 0 ; i < list.size(); i++){
                if(this.DFHasService(list.get(i), sessionKey)){
                    dest = list.get(i);
                    Alert("FOUND AGENT DEST" + dest);
                }
            }

            destFounded = true;
        }
    }
    
        protected Status doQueryPeople(String type) {
        Info("Querying people " + type);
        outbox = session.createReply();
        outbox.setContent("Query " + type.toUpperCase() 
                          + " session " + sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        Message("Found " + getEnvironment().getPeople().length + " " 
                + type + " in " +getEnvironment().getCurrentCity());
        
        if(report == ""){
            report += "REPORT;" + getEnvironment().getCurrentCity() + " " 
                      + type.toLowerCase() + " " 
                      + getEnvironment().getPeople().length;
            actualCity = getEnvironment().getCurrentCity();
        }else{
            if (!getEnvironment().getCurrentCity().equals(actualCity)){
                report += ";" + getEnvironment().getCurrentCity();
                actualCity = getEnvironment().getCurrentCity();
            }

            report += " " + type.toLowerCase() + " " 
                      + getEnvironment().getPeople().length;                
        }
        
        return myStatus;
    }
    
    public Status doCapture(){
        ArrayList<String> capturers = this.DFGetAllProvidersOf("TYPE MTT");
        ArrayList<String> capturersList = new ArrayList<>();
        String objectivesList = "Name";
        if (!capturers.isEmpty()) {
            for (int i=0; i<capturers.size(); i++)
                if (this.DFHasService(capturers.get(i), sessionKey))
                    capturersList.add(capturers.get(i));
            int aux = 0;
            
            String capturer = capturersList.get(aux % capturersList.size());
            aux++; //Inutil ahora mismo, no estamos en un bucle
            outbox = new ACLMessage();
            outbox.setSender(getAID());
            outbox.addReceiver(new AID(capturer, AID.ISLOCALNAME));
            outbox.setContent("BACKUP");
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setConversationId(sessionKey);
            outbox.setProtocol("DROIDSHIP");
            this.LARVAsend(outbox);
            session = this.LARVAblockingReceive();
            
            if(session.getPerformative() == ACLMessage.AGREE) {
                session = this.LARVAblockingReceive();
                if (session.getPerformative() == ACLMessage.INFORM){
                    String[] currentGoal = getEnvironment().getCurrentGoal().split(" ");
                    String type = currentGoal[2];
                    int quantity = Integer.parseInt(currentGoal[1]);
                    Info("Querying people " + type);
                    
                    outbox = new ACLMessage();
                    outbox.setSender(getAID());
                    outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
                    outbox.setContent("Query " + type.toUpperCase() + " session " + sessionKey);
                    outbox.setPerformative(ACLMessage.QUERY_REF);
                    outbox.setConversationId(sessionKey);
                    this.LARVAsend(outbox);
                    session = LARVAblockingReceive();
                    
                    getEnvironment().setExternalPerceptions(session.getContent());
                    String[] peopleList = getEnvironment().getPeople();
                    
                    if(quantity <= peopleList.length){  
                        for (int i = 0; i < quantity; i++){
                            Info(E.getCurrentGoal());
                            Message("Request capture " + peopleList[i] + " SESSION " + sessionKey);
                            
                            outbox = session.createReply();
                            outbox.setContent("Request capture " +peopleList[i] + " SESSION " + sessionKey);
                            outbox.setPerformative(ACLMessage.REQUEST);
                            outbox.setConversationId(sessionKey);
                            this.LARVAsend(outbox);
                            session = LARVAblockingReceive();
                            
                            if (session.getPerformative() == ACLMessage.REFUSE){
                                Message("Unable to capture");
                                return Status.CLOSEPROBLEM;
                            }
                            if (session.getPerformative() == ACLMessage.INFORM){
                                outbox = new ACLMessage();
                                outbox.setSender(getAID());
                                outbox.addReceiver(new AID(capturer, AID.ISLOCALNAME));
                                outbox.setContent("CANCEL");
                                outbox.setPerformative(ACLMessage.CANCEL);
                                outbox.setConversationId(sessionKey);
                                outbox.setProtocol("DROIDSHIP");
                                this.LARVAsend(outbox);
                                return Status.SOLVEPROBLEM;
                            }
                        }
                    } else{
                        Message("Not enough "+ type+" to capture in "+getEnvironment().getCurrentCity());
                    }
                }
            }
            
            return Status.SOLVEPROBLEM;
        }
        return Status.CLOSEPROBLEM;
    }
        
    
    public Status doReport(){
        destFounder();
        report += ";";
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(dest, AID.ISLOCALNAME));
        outbox.setContent(report);
        outbox.setPerformative(ACLMessage.INFORM);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        Info("Sended report");
        return Status.SOLVEPROBLEM;
    }
    
    public Status doMoveIn() {        
        if (G(E)) {
            Info("The goal is over");
            //Message("The problem " + problem + " has been solved");
            goalInitiated = false;
            getEnvironment().setNextGoal();
            
            return Status.SOLVEPROBLEM;
        }
                
        behaviour = AgPlan(E, A);
        if (behaviour == null || behaviour.isEmpty()) {
            Alert("Found no plan to execute");
            return Status.CLOSEPROBLEM;
        } else {
            Info("Found plan: " + behaviour.toString());
            while (!behaviour.isEmpty()) {
                a = behaviour.get(0);
                behaviour.remove(0);
                Info("Excuting " + a);
                this.MyExecuteAction(a.getName());
                if (!Ve(E)) {
                    this.Error("The agent is not alive: " + E.getStatus());
                    return Status.CLOSEPROBLEM;
                }
            }
             
            this.MyReadPerceptions();
            return Status.SOLVEPROBLEM;
        }
    }

    protected boolean MyExecuteAction(String action) {
        Info("Executing action " + action);
        outbox = session.createReply();
        outbox.setContent("Request execute " + action + " session " + sessionKey);
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
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
        if (E.isTargetLeft()) {
            if (a.getName().equals("LEFT")) {
                nextWhichwall = "RIGHT";
                nextdistance = E.getDistance();
                nextPoint = E.getGPS();
                return Choice.ANY_VALUE;
            }
        } else {
            if (a.getName().equals("RIGHT")) {
                nextWhichwall = "LEFT";
                nextdistance = E.getDistance();
                nextPoint = E.getGPS();
                return Choice.ANY_VALUE;
            }
        }
        
        return Choice.MAX_UTILITY;
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
    
    public double goFollowWallRight(Environment E, Choice a) {
        if (E.isFreeFrontRight()) {
            return goTurnOnWallRight(E, a);
        } else if (E.isTargetFrontLeft()
                && E.isFreeFrontLeft()
                && E.getDistance() < point.planeDistanceTo(E.getTarget())) {
            return goStopWallRight(E, a);
        } else if (E.isFreeFront()) {
            return goKeepOnWall(E, a);
        } else {
            return goRevolveWallRight(E, a);
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
    
    public double goTurnOnWallRight(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
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
    
    public double goRevolveWallRight(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
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
    
    public double goStopWallRight(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
            this.resetAutoNAV();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    @Override
    protected double U(Environment E, Choice a) {
        if (whichWall.equals("LEFT")) {
            return goFollowWallLeft(E, a);
        } else if (whichWall.equals("RIGHT")) {
            return goFollowWallRight(E, a);
        } else if (!E.isFreeFront()) {
            return goAvoid(E, a);
        } else {
            return goAhead(E, a);
        }
        
    }

    protected boolean MyReadPerceptions() {
        Info("Reading perceptions");
        outbox = session.createReply();
        outbox.setContent("Query sensors session " + sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
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

    protected Status myAssistedNavigation(int goalx, int goaly) {
        Info("Requesting course to " + goalx + " " + goaly);
        outbox = session.createReply();
        outbox.setContent("Request course to " + goalx + " " + goaly + " Session " + sessionKey);
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        return Status.CHECKIN.SOLVEPROBLEM;
    }

    public void resetAutoNAV() {
        nextWhichwall = whichWall = "NONE";
        nextdistance = distance = Choice.MAX_UTILITY;
        nextPoint = point = null;
    }

    public Status MyCloseProblem() {
        Info("Plan = " + preplan);
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        outbox.setPerformative(ACLMessage.CANCEL);
        outbox.setConversationId(sessionKey);
        Info("Closing problem " + problem + ", session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        this.doDestroyNPC();
        return Status.CHECKOUT;
    }

    public String easyPrintPerceptions() {
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
    
    @Override
    protected String chooseMission() {
        Info("Choose mission");
        String m = "";
        if(getEnvironment().getAllMissions().length == 1)
            m = getEnvironment().getAllMissions()[0];
        else
            m = this.inputSelect("Please choose a mission", getEnvironment().getAllMissions(), "");
        
        Info("Selected mission " + m);
        return m;
    }
    
    @Override
    public ACLMessage LARVAblockingReceive(){
        boolean exit = false;
        ACLMessage res = null;
        while (!exit){
            res = super.LARVAblockingReceive();
            if( res.getContent().equals("TRANSPONDER") && res.getPerformative() == ACLMessage.QUERY_REF){
                outbox = res.createReply();
                outbox.setPerformative(ACLMessage.INFORM);
                outbox.setContent(this.Transponder());
                LARVAsend(outbox);
            } else {
                exit = true;
            }
        }
        return res;
    }

}