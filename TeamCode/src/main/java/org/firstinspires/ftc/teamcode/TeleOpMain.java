/*
Copyright (c) 2017 Dark Matter FTC 10337

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted (subject to the limitations in the disclaimer below) provided that
the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of Robert Atkinson nor the names of his contributors may be used to
endorse or promote products derived from this software without specific prior
written permission.

NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS
LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESSFOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package org.firstinspires.ftc.teamcode;

import com.qualcomm.ftccommon.DbgLog;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/**
 * This file provides  Telop driving for Dark Matter 2016-17 robot.
 *
 * The code is structured as an Iterative OpMode
 *
 * This OpMode uses the common HardwareDM hardware class to define the devices on the robot.
 * All device access is managed through the HardwareDM class.
 *
 */
@TeleOp(name="TeleOpMain", group="DM")
// @Disabled

public class TeleOpMain extends OpMode{

    /* Declare OpMode members. */
    HardwareDM robot       = new HardwareDM(); // use the class created to define a robot hardware

    // Drivetrain constants when in Cap Ball Mode
    final double         CAP_DRIVE_SPEED         = -1.0;        // Reverse the direction
    final double         CAP_TURN_SPEED          = 0.8;         // Slow down the turns a bit

    /* Shooter status */
    double               shootSpeed              = robot.SHOOT_DEFAULT;
    static boolean       shootPressed            = false;
    boolean              shooterHot              = false;
    boolean              fireCamHot              = false;    // Starts off stopped at Teleop start
    boolean              fireCamStopRequested    = true;     // Starts off stopped at TeleOp start
    boolean              fireCamPauseDone        = true;     // No pause needed at start since not running
    boolean              fireCamPaused           = false;    // Currently in autopause mode
    ElapsedTime          fireCamTimer            = new ElapsedTime();
    ElapsedTime          fireCamPauseTimer       = new ElapsedTime();
    final double         FIRE_CAM_MIN_TIME       = 000;      // milliseconds
    final double         FIRE_CAM_PAUSE_MIN      = 100;      // milliseconds
    final double         FIRE_CAM_MAX_TIME       = 500;      // milliseconds
    final double         FIRE_CAM_ERR_TIME       = 1500;     // milliseconds
    final double         FIRE_CAM_PAUSE_TIME     = 100;      // milliseconds


    // Keep track of the status of the intake
    boolean              intakeIn                = false;    // intake running forward
    boolean              intakeOut               = false;    // intake running backward
    boolean              intakeInPressed         = false;    // Is intake button pressed
    boolean              intakeOutPressed        = false;    // Is intake button pressed

    /* Servo current positions */
    double               beaconPos               = robot.BEACON_HOME;
    double               pivotPos                = robot.PIVOT_HOME;
    double               liftDeployPos           = robot.LIFT_DEPLOY_HOME;

    boolean              beaconDeployed          = false;
    boolean              pivotDeployed           = false;

    /*  Keep track of whether we have deployed the ball pickup.  Can't move the lift or pivot
        until this is deployed.
     */
    boolean              pickupDeployed          = false;
    boolean              liftMotorUp             = false;
    boolean              liftMotorDown           = false;
    ElapsedTime          pickupDeployTimer       = new ElapsedTime();


    /*
     * Code to run ONCE when the driver hits INIT
     */
    @Override
    public void init() {
        DbgLog.msg("DM10337 -- Starting TeleOpMain Init.");

        /* Initialize the hardware variables.
         * The init() method of the hardware class does all the work here
         * Specifically don't need gyro and range finder so skip it to save time.
         */
        robot.init(hardwareMap, false);

        DbgLog.msg("DM10337 -- Finished robot.init");

    }

    /*
     * Code to run REPEATEDLY after the driver hits INIT, but before they hit PLAY
     */
    @Override
    public void init_loop() {
    }

    /*
     * Code to run ONCE when the driver hits PLAY
     */
    @Override
    public void start() {
        DbgLog.msg("DM10337 -- Start pressed.");
    }

    /*
     * Code to run REPEATEDLY after the driver hits PLAY but before they hit STOP
     */
    @Override
    public void loop() {

        // Temporary read of cam switch
        telemetry.addData("Cam Switch :", robot.camSwitch.isPressed());

        /*
           Driving code -- read joysticks and drive the motors
        */

        //Read thejoysticks -- Y axis is reversed so negate it
        double throttle = -gamepad1.left_stick_y;
        double direction = gamepad1.right_stick_x;

        // Smooth and deadzone the joytick values
        throttle = smoothPowerCurve(deadzone(throttle,0.10));
        direction = smoothPowerCurve(deadzone(direction,0.10));

        // If we deployed into Cap Ball mode the robot drives differently
        if (pickupDeployed) {
            // Slow down the turns since we have a cap ball -- and reverse which is front of robot
            throttle = CAP_DRIVE_SPEED * throttle;
            direction = CAP_TURN_SPEED * direction;
        }

        // Calculate the drive motors for left and right
        double right = throttle - direction;
        double left = throttle + direction;
        // clip the right/left values so that the values never exceed +/- 1
        right = Range.clip(right, -1, 1);
        left = Range.clip(left, -1, 1);

        // And lets drive
        robot.lfDrive.setPower(left);
        robot.lrDrive.setPower(left);
        robot.rfDrive.setPower(right);
        robot.rrDrive.setPower(right);


        /*
            Code for the shooter firing cam
         */

        // Check the sensor switch and time mark it as pressed
        // Also flag that no pause done this cycle
        if (robot.camSwitch.isPressed()) {  // Need to check against the hardware wiring of switch!
            fireCamTimer.reset();           // Keep track of time since we hit switch
            fireCamPauseDone = false;       // Made a full revolution so may need to pause again
        }

        // Check the gamepad inputs and process start/stop
        if (gamepad2.right_trigger <= 0.2) {
            /** This block of code process the conditions when not pressing the fire button
             * There are 5 conditions we have to account for
             * 1>  We are "auto paused" -- in which case its OK to just stop
             * 2>  We are running, but not yet in our targeted stop zone
             * 3>  We are running, and in targeted stop zone so we will stop here
             * 4>  We are running, and it appears the sensor switch isn't working so just stop here
             * 5>  We are running, and past "point of no return" so go 1 more revolution
             * We also have a catchall else in case of some error conditon which forces stop.
             *
             */

            // If we were running, look at timer to see if we are in the "stop here window"
            if (fireCamPaused) {
                // Already paused so we can just go full stop
                stopFireCam();
                fireCamPaused = false;
                fireCamHot = false;
                fireCamPauseDone = true;
                DbgLog.msg("DM10337 -- Stopped while in auto pause mode.");
            } else if (fireCamHot &&
                    (fireCamTimer.milliseconds() < FIRE_CAM_MIN_TIME)) {
                // Requesting stop but not at optimal stop zone yet
                // Keep going but log the event if first time detected
                if (!fireCamStopRequested) {
                    DbgLog.msg("DM10337 -- Fire cam stop requested before reaching target zone.  Keep going.");
                }
            } else if (fireCamHot &&
                    (fireCamTimer.milliseconds() >= FIRE_CAM_MIN_TIME) &&
                    (fireCamTimer.milliseconds() <= FIRE_CAM_MAX_TIME)) {
                // We are running and in our desired range to stop
                stopFireCam();
                fireCamPaused = false;
                fireCamHot = false;
                fireCamPauseDone = true;
                DbgLog.msg("DM10337 -- Stopping firing cam in target zone");
            } else if (fireCamHot &&
                    (fireCamTimer.milliseconds() >= FIRE_CAM_ERR_TIME)) {
                // We are running and didn't see a sensor timer reset
                // Sensor switch probably not working so just stop here
                stopFireCam();
                fireCamPaused = false;
                fireCamHot = false;
                fireCamPauseDone = true;
                DbgLog.msg("DM10337 -- Stopping firing cam -- switch not working");
            } else if (fireCamTimer.milliseconds() >= FIRE_CAM_MAX_TIME) {
                // We are past the point of no return.  Keep going another round
                if (!fireCamStopRequested) {
                    // First time we see this event so log
                    DbgLog.msg("DM10337 -- Requested firing cam stop but too late.  Keep going.");
                }
            } else {
                // Probably already stopped -- but just in case force stop it again
                stopFireCam();
                if (fireCamHot) {
                    DbgLog.msg("DM10337 -- Firing Cam stopped for other reason");

                }
                fireCamPaused = false;
                fireCamHot = false;
                fireCamPauseDone = true;
            }
            fireCamStopRequested = true;        // Keep track if this is the first time we see this button release

        } else if (gamepad2.right_trigger > 0.2) {
            /**
             * We have 3 conditions to account for when fire button is pressed
             * 1>  We are already in "auto pause" mode -- restart the cam if pause is done
             * 2>  Have not yet paused this revolution and we are in "auto pause" range
             * 3>  Any other case -- rotate the firing cam
             */

            if (fireCamPaused) {
                // Already started pause -- is pause done?
                if (fireCamPauseTimer.milliseconds() >= FIRE_CAM_PAUSE_TIME) {
                    // Restart the cam if needed since pause done
                    startFireCam();
                    DbgLog.msg("DM10337 -- Restarting fire cam after auto pause.");
                    fireCamPaused = false;
                    fireCamHot = true;
                }
            } else if (!fireCamPauseDone && fireCamHot &&
                    (fireCamTimer.milliseconds() >= FIRE_CAM_PAUSE_MIN) &&
                    (fireCamTimer.milliseconds() <= FIRE_CAM_MAX_TIME)) {
                    // We are in the potential time window to insert a new auto pause
                    stopFireCam();
                    fireCamPauseDone = true;
                    fireCamPaused = true;
                    fireCamPauseTimer.reset();
                    DbgLog.msg("DM10337 -- Starting fire cam auto pause.");
            } else {
                // Not auto paused
                // So make sure we are running
                startFireCam();
                if (!fireCamHot) {
                    // Wasn't already running so log it
                    DbgLog.msg("DM10337 -- Starting firing cam.");
                }
                fireCamHot = true;
                fireCamPaused = false;
            }

            fireCamStopRequested = false;
        }

        /*
            Code to adjust the shooter flywheel speed
         */
        // Adjust shooter speed
        if (gamepad2.dpad_down && !shootPressed) {
            // Newly pressed  speed down button
            shootSpeed -= robot.SHOOT_SPEED_INCR;
            shootPressed = true;
            DbgLog.msg("DM10337 -- Shooter speed adjusted to " + shootSpeed);
        } else if (gamepad2.dpad_up && !shootPressed) {
            // Newly pressed speed up button
            shootSpeed += robot.SHOOT_SPEED_INCR;
            shootPressed = true;
            DbgLog.msg("DM10337 -- Shooter speed adjusted to " + shootSpeed);
        }
        if (shootPressed && !gamepad2.dpad_down && !gamepad2.dpad_up) {
            // Reset flag since no shoot speed adjustment pressed
            shootPressed = false;
        }
        shootSpeed = Range.clip(shootSpeed, 0.0, 1.0);

        /*
            Code for the shooter flywheels
         */
        if (gamepad2.left_trigger <= 0.2) {
            // Stopped when not pressed
            robot.lShoot.setPower(0.0);
            robot.rShoot.setPower(0.0);
            if (shooterHot) {
                //  already running so log stop event
                shooterHot = false;
                DbgLog.msg("DM10337 -- Stopping shooter flywheels");
            }

        } else if (gamepad2.left_trigger > 0.2) {
            // Running when pressed
            robot.lShoot.setPower(shootSpeed);
            robot.rShoot.setPower(shootSpeed);
            if (!shooterHot) {
                // Was not running before so log stop event
                shooterHot = true;
                DbgLog.msg("DM10337 -- Starting shooter flywheels");
            }
        }

        telemetry.addData("Shoot", shootSpeed);


        /*
            Beacon pusher code
         */
        if (gamepad1.right_bumper) {
            // Pressed so deploy the beacon pusher
            beaconPos = robot.BEACON_MAX_RANGE;
            if (!beaconDeployed) {
                // Newly pressed so log beacon deploy
                beaconDeployed = true;
                DbgLog.msg("DM10337 -- Deploying beacon presser");
            }
        } else {
            // Not pressed so retract it
            beaconPos = robot.BEACON_MIN_RANGE;
            if (beaconDeployed) {
                // Was deployed so log beacon withdrawal
                beaconDeployed = false;
                DbgLog.msg("DM10337 == Beacon presser off");

            }
        }

        // Set the beacon pusher
        beaconPos = Range.clip(beaconPos, robot.BEACON_MIN_RANGE, robot.BEACON_MAX_RANGE);
        robot.beacon.setPosition(beaconPos);

        /*
            Cap ball forks deployment code.  Keep track of whether the cap ball list if deployed.
            For safety, both drivers have to press a button simultaneously to deploy!
            We will use a timer to make sure we don't try and move it too quickly, to propect
            hardware from damage.  Cap ball lift and pivot are disabled until after this timer expires.
         */
        if (gamepad1.left_bumper && gamepad2.left_bumper) {
            if (pickupDeployed == false) {
                // First time we are trying to deploy
                pickupDeployed = true;
                pickupDeployTimer.reset();      // Set timer of how long to wait

                // Deploy the cap ball lift forks
                robot.liftDeploy.setPosition(robot.LIFT_DEPLOY_MIN_RANGE);

                DbgLog.msg("DM10337 -- Deploying the cap ball lift forks");
            }
        }

        /*
            Code for the cap ball lift.  It is disabled until lift forks deployed and enough time
            has elapsed to safely move it.
         */
        if (pickupDeployed == true && pickupDeployTimer.milliseconds() > robot.DEPLOY_WAIT) {
            // The cap ball lift mechanism is ready to go!

            // Process the pivot servo
            if (gamepad1.left_bumper) {
                // Pressed stick so pivot the lift down
                pivotPos = robot.PIVOT_MAX_RANGE;
                if (!pivotDeployed) {
                    pivotDeployed = true;
                    DbgLog.msg("DM10337 -- Pivoting the Lift Down");
                }
            } else {
                // Not pressed so pivot it to the up (default) position
                pivotPos = robot.PIVOT_MIN_RANGE;
                if (pivotDeployed) {
                    pivotDeployed = false;
                    DbgLog.msg("DM10337 -- Pivoting the Lift Up");
                }
            }

            // For safety verify servo position and then move it
            pivotPos = Range.clip(pivotPos, robot.PIVOT_MIN_RANGE, robot.PIVOT_MAX_RANGE);
            robot.pivot.setPosition(pivotPos);

            // And process the lift motor
            if ((gamepad2.right_stick_y < -0.2) && (!robot.liftLimit.isPressed())) {
                // Lift it up
                robot.liftMotor.setPower(robot.LIFT_UP_SPEED);
                if (!liftMotorUp) {
                    // We weren't going up before so log event
                    liftMotorUp = true;
                    liftMotorDown = false;
                    DbgLog.msg("DM10337 -- Cap Ball Lift moving up");
                }
            } else if (gamepad2.right_stick_y > 0.2) {
                // Or drop it down
                robot.liftMotor.setPower(robot.LIFT_DOWN_SPEED);
                if (!liftMotorDown) {
                    // We weren't moving down before so log event
                    liftMotorDown = true;
                    liftMotorUp = false;
                    DbgLog.msg("DM10337 -- Cap Ball Lift moving down");
                }
            } else robot.liftMotor.setPower(0.0);

        }


        /*
            Code for the ball intake
         */
        if (gamepad1.left_trigger > 0.2) {
            // Pressing intake reverse button
            if (!intakeOutPressed) {
                // Haven't read this button press yet so process it
                intakeOutPressed = true;
                if (intakeOut) {
                    // Already running out so stop it
                    robot.intake.setPower(0.0);
                    intakeOut = false;
                    intakeIn = false;
                    DbgLog.msg("DM10337 -- Intake stopped from reverse");
                    telemetry.addData("Intake", "Intake: stopped  ");
                } else {
                    // Not already in reverse so set it so
                    robot.intake.setPower(robot.INTAKE_OUT_SPEED);
                    intakeOut = true;
                    intakeIn = false;
                    DbgLog.msg("DM10337 -- Intake start reverse");
                    telemetry.addData("Intake", "Intake: reverse  ");
                }
            }
        } else {
            // Intake reverse button is not pressed
            intakeOutPressed = false;
        }
        if (gamepad1.right_trigger > 0.2) {
            // Pressing intake forward button
            if (!intakeInPressed) {
                // Haven't read this button press yet so process it
                intakeInPressed = true;
                if (intakeIn) {
                    // Already running out so stop it
                    robot.intake.setPower(0.0);
                    intakeOut = false;
                    intakeIn = false;
                    DbgLog.msg("DM10337 -- Intake stopped from forward");
                    telemetry.addData("Intake", "Intake: stopped  ");
                } else {
                    // Not already in forward so set it so
                    robot.intake.setPower(robot.INTAKE_IN_SPEED);
                    intakeOut = false;
                    intakeIn = true;
                    DbgLog.msg("DM10337 -- Intake start forward");
                    telemetry.addData("Intake", "Intake: forward  ");
                }
            }
        } else {
            // Intake reverse button is not pressed
            intakeInPressed = false;
        }

        if (gamepad1.a) {
            // Emergency stop on intake
            intakeIn = false;
            intakeOut = false;
            robot.intake.setPower(0.0);
        }

        // Finally update the telemetry for this cycle
        telemetry.addData("Range:  ", robot.rangeSensor.getDistance(DistanceUnit.CM));
        updateTelemetry(telemetry);


    }


    /*
     * Code to run ONCE after the driver hits STOP.  Make all motion stops
     */
    @Override
    public void stop() {
        robot.lfDrive.setPower(0.0);
        robot.lrDrive.setPower(0.0);
        robot.rfDrive.setPower(0.0);
        robot.rrDrive.setPower(0.0);
        robot.lShoot.setPower(0.0);
        robot.rShoot.setPower(0.0);
        robot.intake.setPower(0.0);
        robot.liftMotor.setPower(0.0);
        robot.fire.setPower(0.0);
        DbgLog.msg("Teleop Stop Pressed");
    }


    /**
     * This does the cubic smoothing equation on joystick value.
     * Assumes you have already done any deadzone processing.
     *
     * @param x  joystick input
     * @return  smoothed value
     */
    protected double smoothPowerCurve (double x) {
        //double a = this.getThrottle();
        double a = 1.0;         // Hard code to max smoothing
        double b = 0.05;		// Min power to overcome motor stall

        if (x > 0.0)
            return (b + (1.0-b)*(a*x*x*x+(1.0-a)*x));

        else if (x<0.0)
            return (-b + (1.0-b)*(a*x*x*x+(1.0-a)*x));
        else return 0.0;
    }

    /**
     * Add deadzone to a stick value
     *
     * @param rawStick  Raw value from joystick read -1.0 to 1.0
     * @param dz	Deadzone value to use 0 to 0.999
     * @return		Value after deadzone processing
     */
    protected double deadzone(double rawStick, double dz) {
        double stick;

        // Force limit to -1.0 to 1.0
        if (rawStick > 1.0) {
            stick = 1.0;
        } else if (rawStick < -1.0) {
            stick = -1.0;
        } else {
            stick = rawStick;
        }

        // Check if value is inside the dead zone
        if (stick >= 0.0){
            if (Math.abs(stick) >= dz)
                return (stick - dz)/(1 -  dz);
            else
                return 0.0;

        }
        else {
            if (Math.abs(stick) >= dz)
                return (stick + dz)/(1 - dz);
            else
                return 0.0;

        }
    }

    /**
     * Start the firing cam
     */
    public void startFireCam() {
        robot.fire.setPower(1.0);
    }

    /**
     * Stop the firing cam
     * */
    public void stopFireCam() {
        robot.fire.setPower(0.0);
    }
}
