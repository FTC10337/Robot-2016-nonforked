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

import android.graphics.Color;

import com.qualcomm.ftccommon.DbgLog;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;


/**
 * 100 point autonomous main routine.  It processes both blue and red -- based on the amIBlue method return.
 *
 * Strategy:
 * -- Move forward
 * -- Fire 2 particles into center vortex
 * -- Turn and drive towards wall w/ beacons
 * -- Turn parallel to wall
 * -- Drive to find white stripe on first beacon
 * -- Read color sensor, and decide whether to move forward or back to align beacon presser
 * -- Move as needed and press beacon
 * -- Move to 2nd white line and repeat beacon color sense and press
 * -- Drive to center vortex, knock cap ball, and park.
 */

@Autonomous(name="Auto Blue Full 100", group="DM")
// @Disabled
public class Auto100Blue extends LinearOpMode {

    /* Declare OpMode members. */
    HardwareDM         robot   = new HardwareDM ();   // Use a Pushbot's hardware
    private ElapsedTime     runtime = new ElapsedTime();

    // These constants define the desired driving/control characteristics
    // The can/should be tweaked to suite the specific robot drive train.
    static final double     DRIVE_SPEED             = 0.8;     // Nominal speed for auto moves.
    static final double     DRIVE_SPEED_SLOW        = 0.65;     // Slower speed where required
    static final double     TURN_SPEED              = 1.0;     // Turn speed

    static final double     HEADING_THRESHOLD       = 2 ;      // As tight as we can make it with an integer gyro
    static final double     P_TURN_COEFF            = 0.010;   // Larger is more responsive, but also less accurate
    static final double     P_DRIVE_COEFF_1         = 0.05;  // Larger is more responsive, but also less accurate
    static final double     P_DRIVE_COEFF_2         = 0.03;

    // Coeff for doing range sensor driving -- 1.25 degrees per CM off
    static final double     P_DRIVE_COEFF_3         = 1.25;
    static final double     RANGE_THRESHOLD         = 1.0;  // OK at +/- 1cm
    static final double     WALL_DISTANCE           = 10.0; // 11 cm from wall for beacons

    // White line finder thresholds
    static final double     WHITE_THRESHOLD         = 2.0;      // Line finder

    // Beacon color sensor thresholds
    static final double     BEACON_ALPHA_MIN        = 100.0;
    static final double     BLUE_MIN                = -180;
    static final double     BLUE_MAX                = -100;
    static final double     RED_MIN                 = -40;
    static final double     RED_MAX                 = 40;

    // Variables used for reading Gyro
    Orientation             angles;
    double                  headingBias = 0.0;            // Gyro heading adjustment

    // Keep track of how far we moved to line up to press beacons
    double distCorrection = 0.0;


    // Storage for reading adaFruit color sensor for beacon sensing
    // adaHSV is an array that will hold the hue, saturation, and value information.
    float[] adaHSV = {0F, 0F, 0F};
    // adaValues is a reference to the adaHSV array.
    final float adaValues[] = adaHSV;

    /**
     * The main routine of the OpMode.
     *
     * @throws InterruptedException
     */
    @Override
    public void runOpMode() throws InterruptedException {

        int beacon = 0;         // What color beacon do we see

        DbgLog.msg("DM10337- Starting Auto 100 init.  We are:" + (amIBlue()?"Blue":"Red"));

        // Init the robot hardware -- including gyro and range finder
        robot.init(hardwareMap, true);

        // And turn on the LED on stripe finder
        robot.stripeColor.enableLed(true);

        // Force reset the drive train encoders.  Do it twice as sometimes this gets missed due to USB congestion
        robot.setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        idle();
        robot.setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        idle();
        robot.setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);
        idle();
        robot.setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);

        DbgLog.msg("DM10337 -- Drive train encoders reset");

        DbgLog.msg("DM10337- Finished Init");

        // Show telemetry for gyro status
        telemetry.addData("IMU calibrated: ", robot.adaGyro.isSystemCalibrated());
        telemetry.addData("IMU Gyro calibrated:  ", robot.adaGyro.isGyroCalibrated());
        telemetry.update();

        // Wait for the game to start (driver presses PLAY)

        // Set a timer of how often to update gyro status telemetry
        ElapsedTime updateGyroStatTimer = new ElapsedTime();
        updateGyroStatTimer.reset();
        while (!isStarted()) {
            if (updateGyroStatTimer.milliseconds() >= 500) {
                // Update telemetry every 0.5 seconds
                telemetry.addData("IMU calibrated: ", robot.adaGyro.isSystemCalibrated());
                telemetry.addData("IMU Gyro calibrated:  ", robot.adaGyro.isGyroCalibrated());

                // Do a gyro read to keep it "fresh"
                telemetry.addData("Gyro heading: ", readGyro());
                telemetry.update();

                // And reset the timer
                updateGyroStatTimer.reset();
            }
            idle();
        }

        DbgLog.msg("DM10337- Auto Pressed Start");
        // Step through each leg of the path,

        // Make sure the gyro is zeroed
        zeroGyro();

        DbgLog.msg("DM10337 - Gyro bias set to " + headingBias);

        // Spin up the shooter
        robot.lShoot.setPower(robot.SHOOT_DEFAULT);
        robot.rShoot.setPower(robot.SHOOT_DEFAULT);

        // Move forward  to line up for shooting particles
        // Use gyro to hold heading
        encoderDrive(DRIVE_SPEED,  25.75, 5.0, true, 0.0, false);

        // Fire the balls
        robot.fire.setPower(1.0);
        sleep(1500);        // Wait for shot to finish

        // Turn towards the beacons using gyro
        gyroTurn(TURN_SPEED, amIBlue()?-75.0:65.0);

        // Stop the shooter
        robot.fire.setPower(0.0);
        robot.lShoot.setPower(0.0);
        robot.rShoot.setPower(0.0);

        // Drive towards the beacon wall
        // Use gyro to hold heading
        encoderDrive(DRIVE_SPEED_SLOW, amIBlue()?73:52.5, 5.0,
                true, amIBlue()?-42.0:66.0, false);

        // Turn parallel to beacon wall using gyro
        // Had to tweak the red direction off of 180 to correct alignment error after turn
        gyroTurn(TURN_SPEED, amIBlue()?0.0:175.0);

        // Move slowly to approach 1st beacon -- Slow allows us to be more accurate w/ alignment
        // Autocorrects any heading errors while driving
        encoderDrive(DRIVE_SPEED_SLOW, amIBlue()?-12.5:1.0, 5.0, true,
                amIBlue()?0.0:180.0, false, true, WALL_DISTANCE);

        // Use line finder to align to white line
        findLine(amIBlue()?-0.2:0.2, 3.0);

        // Wait for beacon color sensor
        sleep(1000);

        // Check the beacon color
        beacon = beaconColor();
        if (beacon == 1) {
            // We see blue
            distCorrection = amIBlue()?2.00:-3.75;
        } else if (beacon == -1) {
            // We see red
            distCorrection = amIBlue()?-3.75:2.50;
        } else {
            // We see neither
            distCorrection = 0;
        }

        if (beacon != 0) {
            // We saw a beacon color so move to align beacon pusher
            // We turn by +/- 4 degrees to account for curved front of beacon
            encoderDrive(DRIVE_SPEED, distCorrection, 2.5, true,
                    amIBlue()?(0-4*beacon):(180+5*beacon), true);

            // And press the beacon button
            robot.beacon.setPosition(robot.BEACON_MAX_RANGE);
            sleep (1500);
            robot.beacon.setPosition((robot.BEACON_HOME));

        }

        // Drive to the 2nd beacon.  Tweaked Red heading to correct alignment errors.
        // Use rangefinder correction to get us to 10cm from all
        encoderDrive(DRIVE_SPEED, amIBlue()?42.0 - distCorrection:-44.0 - distCorrection, 4.0,
                true, amIBlue()?0.0:180.0, false, true, WALL_DISTANCE);

        // Find the 2nd white line
        findLine(amIBlue()?0.2:-0.2, 3.0);

        // wait for color sensor
        sleep(1000);

        // Check the beacon color
        beacon = beaconColor();
        if (beacon == 1) {
            // I see blue
            distCorrection = amIBlue()?2.00:-3.50;
        } else if (beacon == -1) {
            // I see red
            distCorrection = amIBlue()?-3.75:2.50;
        } else {
            // I see neither
            distCorrection = 0;
        }

        if (beacon != 0) {
            // We saw a beacon color so move to align beacon pusher
            // Adjust by +/- 4 degrees to account for curved front of beacon
            encoderDrive(DRIVE_SPEED, distCorrection, 2.5, true,
                    amIBlue()?(0-4*beacon):(180+5*beacon), true);

            // And press the beacon button
            robot.beacon.setPosition(robot.BEACON_MAX_RANGE);
            sleep (1500);
            robot.beacon.setPosition((robot.BEACON_HOME));

        }

        // Reverse the intake to keep any particles or cap balls out of our way
        robot.intake.setPower(-1.0);

        // And drive to the center vortex, knock cap ball, and park
        // Note that we are turning while moving to save time at the expense of accuracy
        encoderDrive(1.0, amIBlue()?-75.0:74.0, 10.0, true,
                amIBlue()?-60.0:245, false);

        // And stop
        robot.intake.setPower(0.0);

        DbgLog.msg("DM10337- Finished last move of auto");
        sleep(10000);
        robot.intake.setPower(0.0);



        telemetry.addData("Path", "Complete");
        telemetry.update();
    }

    /*
     *
     */


    /**
     * Abbreviated call to encoderDrive w/o range aggressive turning or finding adjustments
     *
     * @param speed
     * @param distance
     * @param timeout
     * @param useGyro
     * @param heading
     * @throws InterruptedException
     */
    public void encoderDrive(double speed,
                             double distance,
                             double timeout,
                             boolean useGyro,
                             double heading) throws InterruptedException {
        encoderDrive(speed, distance, timeout, useGyro, heading, false, false, 0.0);
    }


    /**
     * Abbreviated call to encoderDrive w/o range finding adjustments
     *
     * @param speed
     * @param distance
     * @param timeout
     * @param useGyro
     * @param heading
     * @param aggressive
     * @throws InterruptedException
     */
    public void encoderDrive(double speed,
                             double distance,
                             double timeout,
                             boolean useGyro,
                             double heading,
                             boolean aggressive) throws InterruptedException {
        encoderDrive(speed, distance, timeout, useGyro, heading, aggressive, false, 0.0);
    }

    /**
     *
     * Method to perfmorm a relative move, based on encoder counts.
     *  Encoders are not reset as the move is based on the current position.
     *  Move will stop if any of three conditions occur:
     *  1) Move gets to the desired position
     *  2) Move runs out of time
     *  3) Driver stops the opmode running.
     *
     * @param speed                 Motor power (0 to 1.0)
     * @param distance              Inches
     * @param timeout               Seconds
     * @param useGyro               Use gyro to keep/curve to an absolute heading
     * @param heading               Heading to use
     * @throws InterruptedException
     */
    public void encoderDrive(double speed,
                             double distance,
                             double timeout,
                             boolean useGyro,
                             double heading,
                             boolean aggressive,
                             boolean userange,
                             double maintainRange) throws InterruptedException {

        // Calculated encoder targets
        int newLFTarget;
        int newRFTarget;
        int newLRTarget;
        int newRRTarget;

        // The potentially adjusted current target heading
        double curHeading = heading;

        // Speed ramp on start of move to avoid wheel slip
        final double MINSPEED = 0.30;           // Start at this power
        final double SPEEDINCR = 0.015;         // And increment by this much each cycle
        double curSpeed;                        // Keep track of speed as we ramp

        // Ensure that the opmode is still active
        if (opModeIsActive()) {

            DbgLog.msg("DM10337- Starting encoderDrive speed:" + speed +
                    "  distance:" + distance + "  timeout:" + timeout +
                    "  useGyro:" + useGyro + " heading:" + heading);

            // Calculate "adjusted" distance  for each side to account for requested turn during run
            // Purpose of code is to have PIDs closer to finishing even on curved moves
            // This prevents jerk to one side at stop
            double leftDistance = distance;
            double rightDistance = distance;
            if (useGyro) {
                // We are gyro steering -- are we requesting a turn while driving?
                double headingChange = getError(curHeading);
                if (Math.abs(headingChange) > 5.0) {
                    //Heading change is significant enough to account for
                    if (headingChange > 0.0) {
                        // Assume 16 inch wheelbase
                        // Add extra distance to the wheel on outside of turn
                        rightDistance += 2 * 3.1415 * 16 * headingChange / 360.0;
                        DbgLog.msg("DM10337 -- Turn adjusted R distance:" + rightDistance);
                    } else {
                        // Assume 16 inch wheelbase
                        // Add extra distance from the wheel on inside of turn
                        // headingChange is - so this is increasing the left distance
                        leftDistance -= 2 * 3.1415 * 16 * headingChange / 360.0;
                        DbgLog.msg("DM10337 -- Turn adjusted L distance:" + leftDistance);
                    }
                }
            }

            // Determine new target encoder positions, and pass to motor controller
            newLFTarget = robot.lfDrive.getCurrentPosition() + (int)(leftDistance * robot.COUNTS_PER_INCH);
            newLRTarget = robot.lrDrive.getCurrentPosition() + (int)(leftDistance * robot.COUNTS_PER_INCH);
            newRFTarget = robot.rfDrive.getCurrentPosition() + (int)(rightDistance * robot.COUNTS_PER_INCH);
            newRRTarget = robot.rrDrive.getCurrentPosition() + (int)(rightDistance * robot.COUNTS_PER_INCH);

            while(robot.lfDrive.getTargetPosition() != newLFTarget){
                robot.lfDrive.setTargetPosition(newLFTarget);
                sleep(1);
            }
            while(robot.rfDrive.getTargetPosition() != newRFTarget){
                robot.rfDrive.setTargetPosition(newRFTarget);
                sleep(1);
            }
            while(robot.lrDrive.getTargetPosition() != newLRTarget){
                robot.lrDrive.setTargetPosition(newLRTarget);
                sleep(1);
            }
            while(robot.rrDrive.getTargetPosition() != newRRTarget){
                robot.rrDrive.setTargetPosition(newRRTarget);
                sleep(1);
            }

            // Turn On motors to RUN_TO_POSITION
            robot.setDriveMode(DcMotor.RunMode.RUN_TO_POSITION);

            // reset the timeout time and start motion.
            runtime.reset();

            speed = Math.abs(speed);    // Make sure its positive
            curSpeed = Math.min(MINSPEED,speed);

            // Set the motors to the starting power
            robot.lfDrive.setPower(Math.abs(curSpeed));
            robot.rfDrive.setPower(Math.abs(curSpeed));
            robot.lrDrive.setPower(Math.abs(curSpeed));
            robot.rrDrive.setPower(Math.abs(curSpeed));

            // keep looping while we are still active, and there is time left, until at least 1 motor reaches target
            while (opModeIsActive() &&
                   (runtime.seconds() < timeout) &&
                    robot.lfDrive.isBusy() &&
                    robot.lrDrive.isBusy() &&
                    robot.rfDrive.isBusy() &&
                    robot.rrDrive.isBusy()) {

                // Ramp up motor powers as needed
                if (curSpeed < speed) {
                    curSpeed += SPEEDINCR;
                }
                double leftSpeed = curSpeed;
                double rightSpeed = curSpeed;

                // Doing gyro heading correction?
                if (useGyro){

                    // Get the difference in distance from wall to desired distance
                    double errorRange = robot.rangeSensor.getDistance(DistanceUnit.CM) -
                            maintainRange;

                    if (userange) {
                        if (Math.abs(errorRange) >= RANGE_THRESHOLD) {
                            // We need to course correct to right distance from wall
                            // Have to adjust sign based on heading forward or backward
                            curHeading = heading - Math.signum(distance) * errorRange * P_DRIVE_COEFF_3;
                            DbgLog.msg("DM10337 - Range adjust -- range:" + errorRange + "  heading: " + curHeading);
                        } else {
                            // We are in the right range zone so just use the desired heading w/ no adjustment
                            curHeading = heading;
                        }
                    }

                    // adjust relative speed based on heading
                    double error = getError(curHeading);
                    double steer = getSteer(error,
                            (aggressive?P_DRIVE_COEFF_1:P_DRIVE_COEFF_2));

                    // if driving in reverse, the motor correction also needs to be reversed
                    if (distance < 0)
                        steer *= -1.0;

                    // Adjust motor powers for heading correction
                    leftSpeed -= steer;
                    rightSpeed += steer;

                    // Normalize speeds if any one exceeds +/- 1.0;
                    double max = Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed));
                    if (max > 1.0)
                    {
                        leftSpeed /= max;
                        rightSpeed /= max;
                    }

                }

                // And rewrite the motor speeds
                robot.lfDrive.setPower(Math.abs(leftSpeed));
                robot.rfDrive.setPower(Math.abs(rightSpeed));
                robot.lrDrive.setPower(Math.abs(leftSpeed));
                robot.rrDrive.setPower(Math.abs(rightSpeed));

                // Allow time for other processes to run.
                idle();
            }


            DbgLog.msg("DM10337- encoderDrive done" +
                    "  lftarget: " +newLFTarget + "  lfactual:" + robot.lfDrive.getCurrentPosition() +
                    "  lrtarget: " +newLRTarget + "  lractual:" + robot.lrDrive.getCurrentPosition() +
                    "  rftarget: " +newRFTarget + "  rfactual:" + robot.rfDrive.getCurrentPosition() +
                    "  rrtarget: " +newRRTarget + "  rractual:" + robot.rrDrive.getCurrentPosition() +
                    "  heading:" + readGyro());

            // Stop all motion;
            robot.lfDrive.setPower(0);
            robot.rfDrive.setPower(0);
            robot.lrDrive.setPower(0);
            robot.rrDrive.setPower(0);

            // Turn off RUN_TO_POSITION
            robot.setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);
        }
    }

    /**
     * Method to find a white line
     *
     * @param speed             Speed to move.  Can be negative to find moving backwards
     * @param timeout
     * @return
     */
    public boolean findLine(double speed, double timeout) {

        // Try to find white line
        // loop and read the RGB data.
        // Note we use opModeIsActive() as our loop condition because it is an interruptible method.

        // Use brake mode so we stop quicker at line
        robot.setDriveZeroPower(DcMotor.ZeroPowerBehavior.BRAKE);

        runtime.reset();
        while (opModeIsActive() &&
                robot.stripeColor.alpha() < WHITE_THRESHOLD &&
                runtime.seconds() < timeout) {

            // Drive til we see the stripe
            robot.lfDrive.setPower(speed);
            robot.rfDrive.setPower(speed);
            robot.lrDrive.setPower(speed);
            robot.rrDrive.setPower(speed);
            idle();
        }

        // Did we find the line?
        boolean finished = (runtime.seconds() < timeout);

        // Stop moving
        robot.lfDrive.setPower(0.0);
        robot.rfDrive.setPower(0.0);
        robot.lrDrive.setPower(0.0);
        robot.rrDrive.setPower(0.0);

        // And reset to float mode
        robot.setDriveZeroPower(DcMotor.ZeroPowerBehavior.FLOAT);

        return finished;
    }

    /**
     *  Method to spin on central axis to point in a new direction.
     *  Move will stop if either of these conditions occur:
     *  1) Move gets to the heading (angle)
     *  2) Driver stops the opmode running.
     *
     * @param speed Desired speed of turn.
     * @param angle      Absolute Angle (in Degrees) relative to last gyro reset.
     *                   0 = fwd. +ve is CCW from fwd. -ve is CW from forward.
     *                   If a relative angle is required, add/subtract from current heading.
     */
    public void gyroTurn (  double speed, double angle) {

        DbgLog.msg("DM10337- gyroTurn start  speed:" + speed +
            "  heading:" + angle);

        // keep looping while we are still active, and not on heading.
        while (opModeIsActive() && !onHeading(speed, angle, P_TURN_COEFF)) {
            // Allow time for other processes to run.
            // onHeading() does the work of turning us
            idle();
        }

        DbgLog.msg("DM10337- gyroTurn done   heading actual:" + readGyro());
    }


    /**
     * Perform one cycle of closed loop heading control.
     *
     * @param speed     Desired speed of turn.
     * @param angle     Absolute Angle (in Degrees) relative to last gyro reset.
     *                  0 = fwd. +ve is CCW from fwd. -ve is CW from forward.
     *                  If a relative angle is required, add/subtract from current heading.
     * @param PCoeff    Proportional Gain coefficient
     * @return
     */
    boolean onHeading(double speed, double angle, double PCoeff) {
        double   error ;
        double   steer ;
        boolean  onTarget = false ;
        double leftSpeed;
        double rightSpeed;

        // determine turn power based on +/- error
        error = getError(angle);

        if (Math.abs(error) <= HEADING_THRESHOLD) {
            // Close enough so no need to move
            steer = 0.0;
            leftSpeed  = 0.0;
            rightSpeed = 0.0;
            onTarget = true;
        }
        else {
            // Calculate motor powers
            steer = getSteer(error, PCoeff);
            rightSpeed  = speed * steer;
            leftSpeed   = -rightSpeed;
        }

        // Send desired speeds to motors.
        robot.lfDrive.setPower(leftSpeed);
        robot.rfDrive.setPower(rightSpeed);
        robot.lrDrive.setPower(leftSpeed);
        robot.rrDrive.setPower(rightSpeed);

        return onTarget;
    }

    /**
     * getError determines the error between the target angle and the robot's current heading
     * @param   targetAngle  Desired angle (relative to global reference established at last Gyro Reset).
     * @return  error angle: Degrees in the range +/- 180. Centered on the robot's frame of reference
     *          +ve error means the robot should turn LEFT (CCW) to reduce error.
     */
    public double getError(double targetAngle) {

        double robotError;


        // calculate error in -179 to +180 range  (
        robotError = targetAngle - readGyro();
        while (robotError > 180)  robotError -= 360;
        while (robotError <= -180) robotError += 360;
        return robotError;
    }

    /**
     * returns desired steering force.  +/- 1 range.  +ve = steer left
     * @param error   Error angle in robot relative degrees
     * @param PCoeff  Proportional Gain Coefficient
     * @return
     */
    public double getSteer(double error, double PCoeff) {
        return Range.clip(error * PCoeff, -1, 1);
    }

    public int beaconColor () {

        // Return 1 for Blue and -1 for Red
        // convert the RGB adaValues to HSV adaValues.
        Color.RGBToHSV((robot.beaconColor.red() * 255) / 800, (robot.beaconColor.green() * 255) / 800,
                (robot.beaconColor.blue() * 255) / 800, adaHSV);

        // Normalize hue to -180 to 180 degrees
        if (adaHSV[0] > 180.0) {
            adaHSV[0] -= 360.0;
        }

        // Only continue if we see beacon i.e. enough light
        if (robot.beaconColor.alpha() < BEACON_ALPHA_MIN) {
            DbgLog.msg("DM10337- Beacon color read & nothing found   alpha:" +
                robot.beaconColor.alpha());
            return 0;
        }


        // Check for blue
        if (adaHSV[0] > BLUE_MIN && adaHSV[0] < BLUE_MAX) {
            // we see blue so return 1.0
            DbgLog.msg("DM10337- Beacon color found blue  alpha:" +
                robot.beaconColor.alpha() +
                "  hue:" + adaHSV[0] );
            return 1;
        }

        // Check for red
        if (adaHSV[0] > RED_MIN && adaHSV[0] < RED_MAX) {
            //telemetry.addData("beacon", -1);
            //telemetry.update();
            DbgLog.msg("DM10337- Beacon color found red  alpha:" +
                    robot.beaconColor.alpha() +
                    "  hue:" + adaHSV[0] );
            return -1;
        }

        DbgLog.msg("DM10337- Beacon color found neither  alpha:" +
                robot.beaconColor.alpha() +
                "  hue:" + adaHSV[0] );
        return 0;         // We didn't see either color so don't know
    }

    /**
     * Record the current heading and use that as the 0 heading point for gyro reads
     * @return
     */
    void zeroGyro() {
        angles = robot.adaGyro.getAngularOrientation().toAxesReference(AxesReference.INTRINSIC).toAxesOrder(AxesOrder.ZYX);
        headingBias = angles.firstAngle;
    }


    /**
     * Read the current heading direction.  Use a heading bias if we recorded one at start to account for drift during
     * the init phase of match
     *
     * @return      Current heading (Z axis)
     */
    double readGyro() {
        angles = robot.adaGyro.getAngularOrientation().toAxesReference(AxesReference.INTRINSIC).toAxesOrder(AxesOrder.ZYX);
        return angles.firstAngle - headingBias;
    }


    /**
     * Always returns true as we are blue.
     *
     * Red OpMode would extend this class and Override this single method.
     *
     * @return          Always true
     */
    public boolean amIBlue() {
        return true;
    }
}
