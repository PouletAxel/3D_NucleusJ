package gred.nucleus.core;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;
import gred.nucleus.utils.FillingHoles;
import gred.nucleus.utils.Histogram;
import ij.*;
import ij.plugin.ContrastEnhancer;
import ij.plugin.Filters3D;
import ij.plugin.GaussianBlur3D;
import ij.process.*;
import ij.measure.*;
import ij.process.AutoThresholder.Method;
import inra.ijpb.binary.ConnectedComponents;

/**
 * this class allows the realization of segmention method in the image in input. This segmentation
 * is based on the method of Otsu, and we add the maximization of the sphericity (shape parameter)
 * of detected object .
 * 
 * @author Poulet Axel
 *
 */
@SuppressWarnings("deprecation")
public class NucleusSegmentation{
	private int m_bestThreshold = 0;
	/** Segmentation parameters*/
	private double m_volumeMin;
	/** */
	private double m_volumeMax;
	/** */
	private String m_logErrorSeg = "";
  
	/**
	 * Constructor
	 * @param imagePlusInput Image to be segmente
	 */
	public NucleusSegmentation(){	}

	/**
	 * Method which run the process in input image. This image will be segmented, and
	 * the binary image will be saved in a directory. 
	 *  
	 * @param imagePlusInput
	 * @return
	 */
	public ImagePlus run(ImagePlus imagePlusInput){
		IJ.log("Begin segmentation"+imagePlusInput.getTitle());
			
		ImagePlus imagePlusSegmented = applySegmentation (imagePlusInput);
		IJ.log("End segmentation "+imagePlusInput.getTitle()+" "+m_bestThreshold);
		if(m_bestThreshold == 0){
			if(m_logErrorSeg.length()==0){
				IJ.showMessage("Error Segmentation", "Bad parameter for the segmentation, any object is detected between "
    				  +m_volumeMin+" and "+ m_volumeMax+" "+ imagePlusInput.getCalibration().getUnit()+"^3");
			}
			else{
				File fileLogError = new File (m_logErrorSeg);
				BufferedWriter bufferedWriterLogError;
				FileWriter fileWriterLogError;
				try{
					fileWriterLogError = new FileWriter(fileLogError, true);
					bufferedWriterLogError = new BufferedWriter(fileWriterLogError);
					bufferedWriterLogError.write(imagePlusInput.getTitle()+"\n");
					bufferedWriterLogError.flush();
					bufferedWriterLogError.close();
				}
				catch(IOException e) { e.printStackTrace(); } 
			}
		}
		return imagePlusSegmented;
	}
	
	/**
	 * Compute of the first threshold of input image with the method of Otsu
	 * From this initial value we will seek the better segmentaion possible:
	 * for this we will take the voxels value superior at the threshold value of method of Otsu :
	 * Then we compute the standard deviation of this values voxel > threshold value
	 * determines which allows range of value we will search the better threshodl value :
	 *   thresholdOtsu-ecartType et thresholdOtsu+ecartType.
	 * For each threshold test; we realize a opening and a closing, then we use 
	 * the holesFilling. To finish we compute the sphericity.
	 * The aim of this method is to maximize the sphericity to obtain the segmented object
	 * nearest of the biological object.
	 * 
	 * @param imagePlusInput
	 * @return
	 */

	public ImagePlus applySegmentation(ImagePlus imagePlusInput){
		double sphericityMax = -1.0;
		double sphericity;
		double volume;
		Calibration calibration = imagePlusInput.getCalibration();
		final double xCalibration = calibration.pixelWidth;
		final double yCalibration = calibration.pixelHeight;
		final double zCalibration = calibration.pixelDepth;
		final double imageVolume = xCalibration*imagePlusInput.getWidth()*yCalibration*imagePlusInput.getHeight()*zCalibration*imagePlusInput.getStackSize();
		IJ.log("new "+xCalibration+" "+yCalibration+" "+zCalibration+"  volume image :"+imageVolume);
		ImagePlus imagePlusSegmented = new ImagePlus();
		ArrayList<Integer> arrayListThreshold = computeMinMaxThreshold(imagePlusInput);	
		IJ.log("Lower limit: "+arrayListThreshold.get(0)+" Upper limit "+arrayListThreshold.get(1));
		for (int t = arrayListThreshold.get(0) ; t <= arrayListThreshold.get(1); ++t){
			ImagePlus imagePlusSegmentedTemp = generateSegmentedImage(imagePlusInput,t);
			Measure3D measure3D = new Measure3D();
			volume = measure3D.computeVolumeObject(imagePlusSegmentedTemp,255);
			if (testRelativeObjectVolume(volume,imageVolume)){	
				morphologicalCorrection (imagePlusSegmentedTemp);
				imagePlusSegmentedTemp = ConnectedComponents.computeLabels(imagePlusSegmentedTemp, 26, 32);
				deleteArtefact(imagePlusSegmentedTemp);
				imagePlusSegmentedTemp.setCalibration(calibration);
				
				if (isVoxelThresholded(imagePlusSegmentedTemp, 255,0) == false && 
						isVoxelThresholded(imagePlusSegmentedTemp, 255,imagePlusSegmentedTemp.getStackSize()-1) == false){
					volume = measure3D.computeVolumeObject(imagePlusSegmentedTemp,255);
					sphericity = measure3D.computeSphericity(volume,measure3D.computeSurfaceObject(imagePlusSegmentedTemp, 255));
					
					if (sphericity > sphericityMax && volume >= m_volumeMin && volume <= m_volumeMax){
						m_bestThreshold=t;
						sphericityMax = sphericity;
						StackConverter stackConverter = new StackConverter( imagePlusSegmentedTemp );
						stackConverter.convertToGray8();
						imagePlusSegmented= imagePlusSegmentedTemp.duplicate();			
					}
				}
			}
		}
		imagePlusSegmented.setCalibration(calibration);
		return imagePlusSegmented;
	}

	
	/**
	 * Compute the beginig threshold value
	 * 
	 * @param imagePlusInput raw image
	 * @return
	 */
	private int computeThreshold(ImagePlus imagePlusInput){
		AutoThresholder autoThresholder = new AutoThresholder();
		ImageStatistics imageStatistics = new StackStatistics(imagePlusInput);
		int [] tHisto = imageStatistics.histogram;
		return autoThresholder.getThreshold(Method.Otsu,tHisto);
	}

	/**
	 * Creation of the nucleus segmented image
	 * 
	 * @param imagePlusInput raw image
	 * @param threshold threshold value for the segmentation
	 * @return segmented image of the nucleus
	 */
	private ImagePlus generateSegmentedImage (ImagePlus imagePlusInput, int threshold){
		ImageStack imageStackInput = imagePlusInput.getStack();
		ImagePlus imagePlusSegmented = imagePlusInput.duplicate();
		ImageStack imageStackSegmented = imagePlusSegmented.getStack();
		for(int k = 0; k < imagePlusInput.getStackSize(); ++k)
			for (int i = 0; i < imagePlusInput.getWidth(); ++i )
				for (int j = 0; j < imagePlusInput.getHeight(); ++j){
					double voxelValue = imageStackInput.getVoxel(i,j,k);
					if (voxelValue >= threshold)
						imageStackSegmented.setVoxel(i,j,k,255);
					else
						imageStackSegmented.setVoxel(i,j,k,0);
				}
		return imagePlusSegmented;
	}

	/**
	 * Determine of the minimum and the maximum value o find the better threshold value
	 * 
	 * @param imagePlusInput raw image
	 * @return array lis which contain at the index 0 the min valu and index 1 the max value
	 * 
	 */
	private ArrayList<Integer> computeMinMaxThreshold(ImagePlus imagePlusInput){
		ArrayList<Integer> arrayListMinMaxThreshold = new ArrayList<Integer>();
		int threshold = computeThreshold(imagePlusInput);
		StackStatistics stackStatistics = new StackStatistics(imagePlusInput);
		double stdDev =stackStatistics.stdDev ;
		double min = threshold - stdDev*2;
		double max = threshold + stdDev;
		if(min < 0) 
			arrayListMinMaxThreshold.add(1);
		else
			arrayListMinMaxThreshold.add((int)min);
		arrayListMinMaxThreshold.add((int)max);
		return arrayListMinMaxThreshold;
	}
	
	/**
	 * Determines there 
	 * 
	 * @param imagePlusSegmented
	 * @param threshold
	 * @param stackIndice
	 * @return
	 */
	private boolean isVoxelThresholded(ImagePlus imagePlusSegmented, int threshold, int stackIndice){
		boolean voxelThresolded = false;
		int nbVoxelThresholded = 0;
		ImageStack imageStackSegmented = imagePlusSegmented.getStack();
		for (int i = 0; i < imagePlusSegmented.getWidth(); ++i){	
			for (int j = 0; j < imagePlusSegmented.getHeight(); ++j){
				//IJ.log("i "+i+" j"+j+" stackIndice "+stackIndice);
				if(imageStackSegmented.getVoxel(i,j,stackIndice) >= threshold)
					nbVoxelThresholded++;
			}
		}
		if (nbVoxelThresholded > 0)
			voxelThresolded = true;
		return voxelThresolded;
	}
	
	
	/**
	 * 	 method to realise sevral morphological correction ( filling holes and top hat)
	 * 
	 * @param imagePlusSegmented image to be correct
	 */
	private void morphologicalCorrection(ImagePlus imagePlusSegmented){
		FillingHoles holesFilling = new FillingHoles();
		computeOpening(imagePlusSegmented);
		computeClosing(imagePlusSegmented);
		imagePlusSegmented = holesFilling.apply2D(imagePlusSegmented);
	}


	/**
	 * compute closing with the segmented image
	 * 
	 * @param imagePlusInput image segmented
	 */
	private void computeClosing(ImagePlus imagePlusInput){
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MAX,1,1,(float)0.5);
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MIN,1,1,(float)0.5);
		imagePlusInput.setStack(imageStackInput);
	}

	/**
	 * compute opening with the segmented image 
	 * 
	 * @param imagePlusInput image segmented
	 */
	private void computeOpening(ImagePlus imagePlusInput){
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MIN,1,1,(float)0.5);
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MAX,1,1,(float)0.5);
		imagePlusInput.setStack(imageStackInput);
	}
  

	/**
	 * getter to retun the chosen threshold value
	 * @return the final threshold value
	 */
	public int getBestThreshold(){
		return m_bestThreshold;
	}
	
	/**
	 * if the detected object is superior or equal at 70% of the image return false
	 *  
	 * @param objectVolume
	 * @return
	 */
	private boolean testRelativeObjectVolume(double objectVolume,double imageVolume){
		final double ratio = (objectVolume/imageVolume)*100;
		if (ratio >= 70) 
			return false;
		else
			return true;
	}
	
	/**
	 * interval of volume to detect the object
	 * 
	 * @param volumeMin
	 * @param volumeMax
	 */
	public void setVolumeRange(double volumeMin, double volumeMax){
		m_volumeMin = volumeMin;
		m_volumeMax = volumeMax;
	}
	 
	/**
	 * 
	 * @param logErrorSeg
	 */
	public void setLogErrorSegmentationFile (String logErrorSeg){
		m_logErrorSeg = logErrorSeg;
	}
	
	/**
	 * Preserve the larger object and remove the other
	 *
	 * @param imagePluslab Image labeled
	 */

	public void deleteArtefact(ImagePlus imagePlusInput){
	    double voxelValue;
	    double mode = getLabelOfLargestObject(imagePlusInput);
	    ImageStack imageStackInput = imagePlusInput.getStack();
	    for(int k = 0; k < imagePlusInput.getNSlices(); ++k)
	    	for (int i = 0; i < imagePlusInput.getWidth(); ++i)
	    		for (int j = 0; j < imagePlusInput.getHeight(); ++j){
	    			voxelValue = imageStackInput.getVoxel(i,j,k);
	    			if (voxelValue == mode)
	    				imageStackInput.setVoxel(i,j,k,255);
	    			else
	    				imageStackInput.setVoxel(i,j,k,0);
	    		}
	}

	/**
	 * Browse each object of image and return the label of the larger object
	 * @param imagePluslab Image labeled
	 * @return Label of the larger object
	 */

	public double getLabelOfLargestObject(ImagePlus imagePlusInput){
		Histogram histogram = new Histogram();
		histogram.run(imagePlusInput);
	    double indiceNbVoxelMax = 0;
	    double nbVoxelMax = -1;
	    for(Entry<Double, Integer> entry : histogram.getHistogram().entrySet()){
	    	double label = entry.getKey();
	        int nbVoxel = entry.getValue();
	        if (nbVoxel > nbVoxelMax){
	        	nbVoxelMax = nbVoxel;
	        	indiceNbVoxelMax = label;
	        }
	    }
	    return indiceNbVoxelMax;
	}
}