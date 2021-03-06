package de.bit.pl2.p3;

import hr.irb.fastRandomForest.FastRandomForest;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.process.ImageConverter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import trainableSegmentation.WekaSegmentation;
import trainableSegmentation.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class WekaSeg {
    /**
     * Train a WekaSegmentation classifier with a pair of image/binary labels.
     * Uses FastRandomForest with default parameters and training features.
     *
     * @param image  training RGB image
     * @param labels training binary image
     * @return trained WegaSegmentation instance
     */
    public WekaSegmentation trainClassifier(ImagePlus image, ImagePlus labels) {
        System.out.println("***** Start training *****");
        // starting time
        Long startTime = System.currentTimeMillis();

        // create Weka segmentator
        WekaSegmentation seg = new WekaSegmentation(image);

        // classifier
        FastRandomForest rf = new FastRandomForest();

        // set classifier
        seg.setClassifier(rf);

        // update classLabel
        seg.setClassLabels(new String[]{"background", "plant"});

        // selected attributes (image features)
        boolean[] enableFeatures = new boolean[]{
                true,   /* Gaussian_blur */
                true,   /* Sobel_filter */
                true,   /* Hessian */
                true,   /* Difference_of_gaussians */
                true,   /* Membrane_projections */
                false,  /* Variance */
                false,  /* Mean */
                false,  /* Minimum */
                false,  /* Maximum */
                false,  /* Median */
                false,  /* Anisotropic_diffusion */
                false,  /* Bilateral */
                false,  /* Lipschitz */
                false,  /* Kuwahara */
                false,  /* Gabor */
                false,  /* Derivatives */
                false,  /* Laplacian */
                false,  /* Structure */
                false,  /* Entropy */
                false   /* Neighbors */
        };

        // enable features in the segmentator
        seg.setEnabledFeatures(enableFeatures);

        // add labeled samples in a balanced and random way
        seg.addRandomBalancedBinaryData(image, labels, "plant", "background", 1000);

        // train classifier
        seg.trainClassifier();

        // save stuff
        seg.saveClassifier("classifier.model");
        seg.saveData("data.arff");

        // print elapsed time
        Long estimatedTime = System.currentTimeMillis() - startTime;
        System.out.println(estimatedTime);

        return seg;
    }

    /**
     * Applies a trained classifier to a list of RGB images.
     * Loads a classifier from a .model file
     *
     * @param imageList input list with RGB images
     * @param path      path to trained Weka Segmentation classifier
     * @return classifiedImageList
     */
    public List<ImagePlus> applyClassifier(List<ImagePlus> imageList, String path) {
        WekaSegmentation seg = new WekaSegmentation();
        boolean loaded = seg.loadClassifier(path);
        if (loaded) {
            System.out.println("***** Classifier loaded *****");
        } else if (!loaded) {
            System.out.println("***** Can't load classifier *****");
        }
        return applyClassifier(imageList, seg);
    }

    /**
     * Applies a trained classifier to a list of RGB images.
     *
     * @param imageList input list with RGB images
     * @param seg       instance of trained WekaSegmentation
     * @return classifiedImageList
     */
    public List<ImagePlus> applyClassifier(List<ImagePlus> imageList, WekaSegmentation seg) {
        System.out.println("***** Apply classifier to folder *****");
        List<ImagePlus> resultList = new ArrayList<>();

        // iterate over imageList
        for (ImagePlus img : imageList) {
            // apply classifier and get results (0 indicates number of threads is auto-detected)
            ImagePlus result = seg.applyClassifier(img, 0, false);
            result.setLut(Utils.getGoldenAngleLUT());

            // convert from red/green to grayscale
            ImageConverter imageConverter = new ImageConverter(result);
            imageConverter.convertToGray8();
            result.updateImage();

            // get B&W image
            IJ.run(result, "Convert to Mask", "Black Background");
            Prefs.blackBackground = true;
            IJ.run(result, "Make Binary", "white");

            result.setTitle(img.getShortTitle() + "_class");
            resultList.add(result);
        }
        return resultList;
    }

    /**
     * Applies a trained classifier to a list of RGB images.
     * Automatically grabs the classifier from resources.
     *
     * @param imageList input list with RGB images
     * @return classifiedImageList
     */
    public List<ImagePlus> applyClassifier(List<ImagePlus> imageList) {
        WekaSegmentation seg = new WekaSegmentation();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("classifier.model");
            InputStream inputStream = resource.getInputStream();
            boolean loaded = seg.loadClassifier(inputStream);
            if (loaded) {
                System.out.println("***** Classifier loaded *****");
            } else if (!loaded) {
                System.out.println("***** Can't load classifier *****");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return applyClassifier(imageList, seg);
    }
}