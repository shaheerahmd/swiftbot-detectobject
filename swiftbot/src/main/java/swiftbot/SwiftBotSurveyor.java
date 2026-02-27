package swiftbot; //declaring that the class belongs to the package swiftbot

import swiftbot.Button;
import swiftbot.ImageSize;
import swiftbot.SwiftBotAPI;
import swiftbot.Underlight; 
//importing enums and classes from the pacakge that we just made "swiftbot"... required for button control, image size constants, underlights and the main API for controlling the bot
import java.awt.image.BufferedImage; //to work with images
import java.io.File; //creating and writing to files
import java.io.FileWriter; //handling file errors
import java.io.IOException;//handling IO errors
import java.text.SimpleDateFormat;//formatting timestamps
import java.util.Date;
import java.util.Random;//to generate random numbers
import javax.imageio.ImageIO;//reading and writing imagess

public class SwiftBotSurveyor {//declaring class which contain the entire logic 

    private SwiftBotAPI botAPI;//holds instance of bot which is gonna help interacting with bots hardware
    private String operationMode;//stores mode selected by the qr code
    private long startTimeMillis;//stores time in ms of entire run time
    private int objectEncounterCount;//counting num of times objects r encountered
    private String logPath = "logs/bot_log.txt";
    private String imageFolder = "logs/images";//filepath of log file and taken images
    private StringBuilder logText;//string builder to accumulate log messages throughout the execution
    private volatile boolean stopRequested = false; //A flag that indicates if the user has requested the program to stop. Marked volatile so that changes to it are visible across threads.
    
    private static final int TARGET_CURIOUS_DISTANCE = 30;
    private static final int TARGET_SCAREDY_DISTANCE = 50;
    private static final int WANDER_SPEED = 60;
    private static final int NO_OBJECT_LIMIT = 100;//measurements
    private static final double DISTANCE_TOLERANCE = 0.5;//acceptable error +-0.5
    private static final double MS_PER_CM = 33.0;//number of ms to move per cm of the error
    private static final int MAX_MOVE_DURATION = 1500;//max time in ms that one movement command can execute
    
    private Random randomGen = new Random();//random instance used for generating random turns and dubious mode selection
    
    public SwiftBotSurveyor() throws Exception {
        botAPI = new SwiftBotAPI();//initialising swiftbot api
        startTimeMillis = System.currentTimeMillis(); //recording start time
        objectEncounterCount = 0;//setting object encountered to 0
        new File(imageFolder).mkdirs();//creating directories for storing images
        new File("logs").mkdirs();//creating directories for storing logs
        logText = new StringBuilder();}//Initializing the log text container.
    
    public void run() throws Exception {
        operationMode = readModeFromQRCode();//call readmode to get the mode
        if (operationMode.equals("Dubious SwiftBot")) {//if condition for dubious chose either one of the modes
            boolean chooseCurious = randomGen.nextBoolean();
            log("Dubious mode random value: " + chooseCurious);
            operationMode = chooseCurious ? "Curious SwiftBot" : "Scaredy SwiftBot";
            log("Dubious mode behavior selected: " + operationMode);//logs and prints the selecte mode.
       }
        log("Mode selected: " + operationMode);
        System.out.println("Selected Mode: " + operationMode);
        botAPI.fillUnderlights(new int[] {0, 0, 255});//underlights blue->robot wandering
        log("Lights set to blue. Starting wandering behavior...");
        System.out.println("Bot is wandering. Press 'X' on the bot to stop.");
        botAPI.enableButton(Button.X, () -> stopBot());//enabling x button to terminate the program any time
        wanderAround();//start the wandering loop
     }
    
    private String readModeFromQRCode() throws InterruptedException {
        String modeFound = "";
        int invalidScans = 0;
        while (modeFound.isEmpty()) {
            System.out.println("Scan a QR code to select a mode: Curious SwiftBot, Scaredy SwiftBot, or Dubious SwiftBot.");
            BufferedImage qrPic = botAPI.getQRImage();//continusouly ask the user to scan a qr code until valid mode is selevcted
            try {
                modeFound = botAPI.decodeQRImage(qrPic).trim();
             } catch (IllegalArgumentException e) {
                modeFound = "";//capturing qr image and trying to decode it
            }
            if (!(modeFound.equalsIgnoreCase("Curious SwiftBot") ||
                  modeFound.equalsIgnoreCase("Scaredy SwiftBot") ||
                  modeFound.equalsIgnoreCase("Dubious SwiftBot"))) {
                invalidScans++;
                if (invalidScans >= 15) {
                    System.exit(0);
                }
                System.out.println("Invalid QR code. Try again.");
                log("Invalid QR code scanned: " + modeFound);
                modeFound = "";
                Thread.sleep(3000);//If the scanned decoded string not match one of the valid modes it logs the error waits 3 seconds, and tries again.
            } else {
                if (modeFound.equalsIgnoreCase("Curious SwiftBot"))
                    modeFound = "Curious SwiftBot";
                else if (modeFound.equalsIgnoreCase("Scaredy SwiftBot"))
                    modeFound = "Scaredy SwiftBot";
                else if (modeFound.equalsIgnoreCase("Dubious SwiftBot"))
                    modeFound = "Dubious SwiftBot";
            }
        }
        System.out.println("Mode selected: " + modeFound);
        return modeFound;//when a valid mode is detected, it standardize the string and returns it.
    }
    
    private void wanderAround() throws Exception {
        botAPI.startMove(WANDER_SPEED, WANDER_SPEED);//starts movement at the wandering speed
        long lastObjectTime = System.currentTimeMillis();
        while (!stopRequested) {
            double distance = botAPI.useUltrasound();//continously checks the distance from the object using the ultrasonix sensor
            if (operationMode.equals("Scaredy SwiftBot")) {
                if (distance <= TARGET_SCAREDY_DISTANCE) {
                    objectEncounterCount++;
                    log("Object detected at " + distance + " cm. Encounter #" + objectEncounterCount);
                    runModeBehavior(distance);//if distance is less than or equal to 50 cm, it logs an encounter, calls runModeBehavior() for Scaredy behavior, then waits until the object is cleared.
                    while(botAPI.useUltrasound() <= TARGET_SCAREDY_DISTANCE && !stopRequested) {
                        Thread.sleep(500);
                    }
                    botAPI.fillUnderlights(new int[] {0, 0, 255});
                    botAPI.startMove(WANDER_SPEED, WANDER_SPEED);
                    lastObjectTime = System.currentTimeMillis();
                } else {
                    if (System.currentTimeMillis() - lastObjectTime > 5000) {
                        botAPI.stopMove();
                        Thread.sleep(1000);
                        int leftTurnSpeed = WANDER_SPEED + randomGen.nextInt(11) - 5;
                        int rightTurnSpeed = WANDER_SPEED + randomGen.nextInt(11) - 5;
                        botAPI.startMove(leftTurnSpeed, rightTurnSpeed);
                        lastObjectTime = System.currentTimeMillis();
                        log("No object for 5 sec. Changing direction: Left=" + leftTurnSpeed + ", Right=" + rightTurnSpeed);
                    }//if no object detected for 5 secs, change direction by adjusting the wheels
                }
            } else {
                if (distance < NO_OBJECT_LIMIT) {
                    objectEncounterCount++;
                    log("Object detected at " + distance + " cm. Encounter #" + objectEncounterCount);
                    runModeBehavior(distance);//If object detected (distance less than 100 cm), it logs the encounter and calls runModeBehavior() for Curious behavior.
                    botAPI.fillUnderlights(new int[] {0, 0, 255});
                    botAPI.startMove(WANDER_SPEED, WANDER_SPEED);
                    lastObjectTime = System.currentTimeMillis();
                } else {
                    if (System.currentTimeMillis() - lastObjectTime > 5000) {
                        botAPI.stopMove();
                        Thread.sleep(1000);
                        int leftTurnSpeed = WANDER_SPEED + randomGen.nextInt(11) - 5;
                        int rightTurnSpeed = WANDER_SPEED + randomGen.nextInt(11) - 5;
                        botAPI.startMove(leftTurnSpeed, rightTurnSpeed);
                        lastObjectTime = System.currentTimeMillis();
                        log("No object for 5 sec. Changing direction: Left=" + leftTurnSpeed + ", Right=" + rightTurnSpeed);
                    }//If an object is detected (distance less than 100 cm), it logs the encounter and calls runModeBehavior() for Curious behavior.
                }
            }
            Thread.sleep(1000);
        }//this loop runs every one second until stopRequested set to true
    }
    
    private void runModeBehavior(double distance) throws Exception {//checks current mode calling appropriate behavior
        if (operationMode.equals("Curious SwiftBot")) {
            doCuriousMode(distance);
        } else if (operationMode.equals("Scaredy SwiftBot")) {
            doScaredyMode(distance);
        } else {
            log("Invalid mode. Resuming wandering.");
        }
    }
    
    private void doCuriousMode(double distance) throws Exception {
        log("Curious mode started. Initial distance: " + distance + " cm.");//logging starting/initial distance
        botAPI.fillUnderlights(new int[] {0, 255, 0});//turning greenlights on
        final double targetDistance = 30.0;
        final double allowedError = 0.5;//target distance 30cms with an erro tolerance of 0.5cm
        double currentDistance = distance;
        
        while (Math.abs(currentDistance - targetDistance) > allowedError && !stopRequested) {//loop to check distance if above move forward if below move backwards
            if (currentDistance > targetDistance) {
                double extra = currentDistance - targetDistance;
                int duration = (int)(extra * MS_PER_CM);
                if (duration > MAX_MOVE_DURATION) duration = MAX_MOVE_DURATION;
                log("Too far by " + extra + " cm. Moving forward for " + duration + " ms.");
                botAPI.move(60, 60, duration);
            } else {
                double extra = targetDistance - currentDistance;
                int duration = (int)(extra * MS_PER_CM);
                if (duration > MAX_MOVE_DURATION) duration = MAX_MOVE_DURATION;
                log("Too close by " + extra + " cm. Moving backward for " + duration + " ms.");
                botAPI.move(-60, -60, duration);
            }
            Thread.sleep(300);//After each move, it waits 300 ms, then re-reads the sensor.
            if (stopRequested) return;
            currentDistance = botAPI.useUltrasound();
            log("Updated distance: " + currentDistance + " cm.");
        }
        
        log("Target reached (30 cm ±" + allowedError + " cm). Blinking lights.");
        blinkUnderlights(new int[] {0, 255, 0}, 5);//when the gap is in allowed tolerance green lights blink
        
        BufferedImage picture = botAPI.takeStill(ImageSize.SQUARE_720x720);
        String picPath = imageFolder + "/image_" + (++objectEncounterCount) + "_" + getTimestamp() + ".jpg";//take an image and save with a unique file name
        try {
            ImageIO.write(picture, "jpg", new File(picPath));
            log("Picture saved: " + picPath);
        } catch (IOException e) {
            log("Error saving picture: " + e.getMessage());
        }
        
        Thread.sleep(5000);//wait for 5 secs then recheck the distance
        double afterWaitDistance = botAPI.useUltrasound();
        log("Distance after waiting: " + afterWaitDistance + " cm.");
        if (Math.abs(afterWaitDistance - targetDistance) > allowedError && !stopRequested) {
             log("Distance changed significantly. Re-adjusting.");
            doCuriousMode(afterWaitDistance);
        } else {
            log("Distance stable at 30 cm. Waiting 1 sec then turning.");
            Thread.sleep(1000);
            botAPI.stopMove();
            if (randomGen.nextBoolean()) {
                botAPI.move(70, -70, 250);
                log("Turn executed: move(70, -70, 250).");
            } else {
                botAPI.move(-70, 70, 250);
                log("Turn executed: move(-70, 70, 250).");//wait for a second and chose one of 2 to tuurn
            }
        }
        botAPI.fillUnderlights(new int[] {0, 0, 255});//reset underlights to blue
    }
    
    private void doScaredyMode(double distance) throws Exception {
       log("Scaredy mode started at distance: " + distance + " cm.");//begin scaredy mode by logging the detected distancce
        botAPI.stopMove();//stop movement
        BufferedImage picture = botAPI.takeStill(ImageSize.SQUARE_720x720);
        String picPath = imageFolder + "/image_" + (++objectEncounterCount) + "_" + getTimestamp() + ".jpg";//capture n save img
        try {
            ImageIO.write(picture, "jpg", new File(picPath));
            log("Picture saved: " + picPath);
        } catch (IOException e) {
            log("Error saving picture: " + e.getMessage());
        }
        botAPI.fillUnderlights(new int[] {255, 0, 0});
        blinkUnderlights(new int[] {255, 0, 0}, 5);//blink underlights to red
        botAPI.move(-40, -40, 1000);//move backwards for 1 second
        log("Backed up for 1 second.");
        if (randomGen.nextBoolean()) {
            botAPI.move(70, -70, 1000);//move 180 to backwards
            log("Turn executed: move(70, -70, 1000).");
        } else {
            botAPI.move(-70, 70, 1000);
            log("Turn executed: move(-70, 70, 1000).");
        }
        botAPI.move(50, 50, 3000);//move forward for 3 seconds
        log("Moved forward for 3 seconds.");
        double afterMoveDistance = botAPI.useUltrasound();
        log("Distance after scaredy move: " + afterMoveDistance + " cm.");//recheck distance and log
        botAPI.fillUnderlights(new int[] {0, 0, 255});//resetting underlights to bluee
    }
    
    private void blinkUnderlights(int[] color, int times) throws InterruptedException {
       for (int i = 0; i < times && !stopRequested; i++) {//Loops for a specified number of times (or until stop is requested) to blink the underlights:
            botAPI.fillUnderlights(color);//turn light on with some specified colour
            Thread.sleep(200);//wait 200 milliseconds
            botAPI.disableUnderlights();//turn the lights off
            Thread.sleep(200);//wait another 200ms
        }
        botAPI.fillUnderlights(color);//after blinking reset lights t the specified colour
    }
    
    private void stopBot() {
        try {
           stopRequested = true;
            botAPI.stopMove();
            botAPI.disableAllButtons();
            log("Stop requested by user.");
            System.out.println("View execution log? Press 'Y' to view, or 'X' to exit and show log path.");
            botAPI.enableButton(Button.Y, () -> {
                showLog();
                System.exit(0);
            });
            Thread.sleep(500);
            System.exit(0);
        } catch (Exception e) {
            System.out.println("Error during stop: " + e.getMessage());
        }
    }//When Button X is pressed, the stop method sets a flag to exit loops, stops the robot's movement, and disables all buttons. It logs the stop request and prompts the user to view the log with Button Y. After a short delay, the program exits.
    
    private void showLog() {
        System.out.println("---------- Execution Log ----------");
        System.out.println("Mode: " + operationMode);
        long runTime = (System.currentTimeMillis() - startTimeMillis) / 1000;//Prints the execution log details to the console: mode, duration, encounter count, and file paths.
        System.out.println("Duration: " + runTime + " seconds");
        System.out.println("Encounter Count: " + objectEncounterCount);
        System.out.println("Image Folder: " + imageFolder);
        System.out.println("Log File: " + logPath);
        System.out.println("-----------------------------------");
        saveLog();//Calls saveLog() to write the log text to the log file.
    }
    
    private void saveLog() {
        try {
            File file = new File(logPath);//create log file directories
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(logText.toString());//writes accumulated log text into the file 
            }
           log("Log saved at " + logPath);//logs a msg confirming that log was saved
        } catch (IOException e) {
            System.out.println("Log save error: " + e.getMessage());
        }
    }
    
     private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    }//Returns the current date and time formatted as "yyyy-MM-dd_HH-mm-ss" to be used in filenames and log entries.
    
   private void log(String msg) {
        String timeStamp = getTimestamp();
        logText.append("[").append(timeStamp).append("] ").append(msg).append("\n");//appends a log message with the current timestamp to the log text
        System.out.println("LOG: " + msg);//prints msg to console
    }
    
     public static void main(String[] args) {   //entry point of program  
        try {
            SwiftBotSurveyor myBot = new SwiftBotSurveyor();
            myBot.run();//creating instance of swiftbotsurveyor and calls the run() method
        } catch (Exception e) {
            System.out.println("Fatal error: " + e.getMessage());
            e.printStackTrace();//if any exception it prints fatal error message
        }
    }
}
