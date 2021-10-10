package org.firstinspires.ftc.teamcode.util;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import org.openftc.easyopencv.OpenCvPipeline;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
@Autonomous
public class TechiesOpenCVTest extends LinearOpMode
{
    OpenCvCamera webcam;
    FreightFrenzyPipeline pipeline;

    @Override
    public void runOpMode()
    {
        /**
         * NOTE: Many comments have been omitted from this sample for the
         * sake of conciseness. If you're just starting out with EasyOpenCv,
         * you should take a look at {@link InternalCamera1Example} or its
         * webcam counterpart, {@link WebcamExample} first.
         */


        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        webcam = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "Webcam 1"), cameraMonitorViewId);
        pipeline = new FreightFrenzyPipeline();
        webcam.setPipeline(pipeline);

        // We set the viewport policy to optimized view so the preview doesn't appear 90 deg
        // out when the RC activity is in portrait. We do our actual image processing assuming
        // landscape orientation, though.
        webcam.setViewportRenderingPolicy(OpenCvCamera.ViewportRenderingPolicy.OPTIMIZE_VIEW);

        webcam.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                webcam.startStreaming(320,240, OpenCvCameraRotation.UPRIGHT);
            }

            @Override
            public void onError(int errorCode)
             {
            /*
             * This will be called if the camera could not be opened
             */
             }
         });

        waitForStart();

        while (opModeIsActive())
        {
            telemetry.addData("Barcode Number: ", pipeline.getAnalysis());
            telemetry.addData("Average Value of Pixels: ", pipeline.getPixAvg());
            telemetry.update();

            // Don't burn CPU cycles busy-looping in this sample
            sleep(50);
        }
    }

    public static class FreightFrenzyPipeline extends OpenCvPipeline
    {
        private int pixAvg;
        /*
          An enum to define the number of rings in the starter stack
         */
        public enum Barcode {
            ONE,
            TWO,
            THREE,
        }

        /*
        Points which actually define the sample region rectangles, derived from above values

        Example of how points A and B work to define a rectangle

            ------------------------------------
            | (0,0) Point A                    |
            |                                  |
            |                                  |
            |                                  |
            |                                  |
            |                                  |
            |                                  |
            |                  Point B (70,50) |
            ------------------------------------
         */
        static final Scalar YELLOW = new Scalar(255, 255, 0);
        static final Point REGION1_TOPLEFT_POINT = new Point(109,98); // these are placeholders,  find actual values
        static final int REGION_WIDTH = 20;
        static final int REGION_HEIGHT = 20;
        Point region1_pointA = new Point(   REGION1_TOPLEFT_POINT.x, REGION1_TOPLEFT_POINT.y );
        Point region1_pointB = new Point(REGION1_TOPLEFT_POINT.x + REGION_WIDTH, REGION1_TOPLEFT_POINT.y + REGION_HEIGHT);
        Mat region1_Cb;
        Mat YCrCb = new Mat();
        Mat Cb = new Mat();


        // Volatile since accessed by OpMode thread w/o synchronization
        private volatile Barcode number = Barcode.THREE;

        /*
         * This function takes the RGB frame, converts to YCrCb,
         * and extracts the Cb channel to the 'Cb' variable
         */
        void inputToCb(Mat input)
        {
            Imgproc.cvtColor(input, YCrCb, Imgproc.COLOR_RGB2YCrCb);
            Core.extractChannel(YCrCb, Cb, 2);
        }

        @Override
        public void init(Mat firstFrame)
        {
            /*
             * We need to call this in order to make sure the 'Cb'
             * object is initialized, so that the submats we make
             * will still be linked to it on subsequent frames. (If
             * the object were to only be initialized in processFrame,
             * then the submats would become delinked because the backing
             * buffer would be re-allocated the first time a real frame
             * was crunched)
             */
            inputToCb(firstFrame);

            /*
             * Submats are a persistent reference to a region of the parent
             * buffer. Any changes to the child affect the parent, and the
             * reverse also holds true.
             */
            region1_Cb = Cb.submat(new Rect(region1_pointA, region1_pointB));
                /*`region2_Cb = Cb.submat(new Rect(region2_pointA, region2_pointB));
                region3_Cb = Cb.submat(new Rect(region3_pointA, region3_pointB)); */
        }

        @Override
        public Mat processFrame(Mat input)
        {
            /*
             * Overview of what we're doing:
             *
             * We first convert to YCrCb color space, from RGB color space.
             * Why do we do this? Well, in the RGB color space, chroma and
             * luma are intertwined. In YCrCb, chroma and luma are separated.
             * YCrCb is a 3-channel color space, just like RGB. YCrCb's 3 channels
             * are Y, the luma channel (which essentially just a B&W image), the
             * Cr channel, which records the difference from red, and the Cb channel,
             * which records the difference from blue. Because chroma and luma are
             * not related in YCrCb, vision code written to look for certain values
             * in the Cr/Cb channels will not be severely affected by differing
             * light intensity, since that difference would most likely just be
             * reflected in the Y channel.
             *
             * After we've converted to YCrCb, we extract just the 2nd channel, the
             * Cb channel. We do this because stones are bright yellow and contrast
             * STRONGLY on the Cb channel against everything else, including SkyStones
             * (because SkyStones have a black label).
             *
             * We then take the average pixel value of 3 different regions on that Cb
             * channel, one positioned over each stone. The brightest of the 3 regions
             * is where we assume the SkyStone to be, since the normal stones show up
             * extremely darkly.
             *
             * We also draw rectangles on the screen showing where the sample regions
             * are, as well as drawing a solid rectangle over top the sample region
             * we believe is on top of the SkyStone.
             *
             * In order for this whole process to work correctly, each sample region
             * should be positioned in the center of each of the first 3 stones, and
             * be small enough such that only the stone is sampled, and not any of the
             * surroundings.
             */

            /*
             * Get the Cb channel of the input frame after conversion to YCrCb
             */
            inputToCb(input);

            /*
             * Compute the average pixel value of each submat region. We're
             * taking the average of a single channel buffer, so the value
             * we need is at index 0. We could have also taken the average
             * pixel value of the 3-channel image, and referenced the value
             * at index 2 here.
             */
            pixAvg = (int) Core.mean(region1_Cb).val[0];
            int FOURRINGS = 128; // placeholder value - value of avg with 4 rings
            int ONERING = 120; // placeholder value - value of avg with 1 ring





            /* figure out which sample region that value was from
             */
            if (pixAvg > FOURRINGS){
                // 4 rings
                number = Barcode.THREE;
            }
            else if(pixAvg > ONERING){
                // Barcode 2
                number = Barcode.TWO;
            }
            else {
                // Barcode 1
                number = Barcode.ONE;
            }

            /*
             * Render the 'input' buffer to the viewport. But note this is not
             * simply rendering the raw camera feed, because we called functions
             * to add some annotations to this buffer earlier up.
             */
            return input;
        }

        /*
         * Call this from the OpMode thread to obtain the latest analysis
         */
        public Barcode getAnalysis()
        {
            return number;
        }
        public int getPixAvg()
        {

            return pixAvg;
        }
    }
}


