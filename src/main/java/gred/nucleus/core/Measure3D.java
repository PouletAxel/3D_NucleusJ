package gred.nucleus.core;
import gred.nucleus.utils.Histogram;
import gred.nucleus.utils.VoxelRecord;
import ij.*;
import ij.measure.*;

import java.util.ArrayList;
import java.util.HashMap;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;

/**
 * 
 * Class 3D measure contains methods for the computation of different 3D characteristics of 3D binary objects
 *  
 * @author Poulet Axel
 */


public class Measure3D{
	public Measure3D() { }

	/**
	 * Scan of image and if the voxel belong to the object of interest, looking,
	 * if in his neighborhood there are voxel value == 0 then it is a boundary voxel.
	 * Adding the surface of the face of the voxel frontier, which are in contact
	 * with the background of the image, to the surface total.
	 *
	 * @param imagePlusInput segmented image
	 * @param label label of the interest object
	 * @return the surface
	 */

	public double computeSurfaceObject(ImagePlus imagePlusInput, double label){
		Calibration calibration= imagePlusInput.getCalibration();
		ImageStack imageStackInput = imagePlusInput.getStack();
		double xCalibration = calibration.pixelWidth;
		double yCalibration = calibration.pixelHeight;
		double zCalibration = calibration.pixelDepth;
		double surfaceArea = 0,voxelValue, neighborVoxelValue;
		for (int k = 1; k < imagePlusInput.getStackSize()-1; ++k)
			for (int i = 1; i < imagePlusInput.getWidth()-1; ++i)
				for (int j = 1; j < imagePlusInput.getHeight()-1; ++j){
					voxelValue = imageStackInput.getVoxel(i, j, k);
					if (voxelValue == label){
						for (int kk = k-1; kk <= k+1; kk += 2){
							neighborVoxelValue = imageStackInput.getVoxel(i, j, kk);
							if (voxelValue != neighborVoxelValue)
								surfaceArea = surfaceArea + xCalibration * yCalibration;
						}
						for (int ii=i-1; ii<=i+1; ii+=2){
							neighborVoxelValue =  imageStackInput.getVoxel(ii, j, k);
							if (voxelValue != neighborVoxelValue)
								surfaceArea = surfaceArea + xCalibration * zCalibration;
						}
						for (int jj = j-1; jj <= j+1; jj+=2){
							neighborVoxelValue = imageStackInput.getVoxel(i, jj, k);
							if (voxelValue != neighborVoxelValue)
								surfaceArea = surfaceArea + yCalibration * zCalibration;
						}
					}
				}
		return surfaceArea;
	}


	/**
	 * Compute volume in each object conatain in an image
	 * 
	 * @param imagePlusInput segmented image
	 * @return double table containing the volume of each objects
	 */
	public double[] computeVolumeofAllObjects(ImagePlus imagePlusInput){
		Calibration calibration= imagePlusInput.getCalibration();
		double xCalibration = calibration.pixelWidth;
		double yCalibration = calibration.pixelHeight;
		double zCalibration = calibration.pixelDepth;
		Histogram histogram = new Histogram ();
		histogram.run(imagePlusInput);
		double []tlabel = histogram.getLabels();
		double [] tObjectVolume = new double[tlabel.length];
		HashMap<Double , Integer> hashHisto = histogram.getHistogram();
		for(int i=0; i < tlabel.length; ++i){
	        int nbVoxel = hashHisto.get(tlabel[i]); 
			tObjectVolume[i] = nbVoxel*xCalibration*yCalibration*zCalibration;
	    }
		return tObjectVolume;
	} 

	/**
	 * Compute the volume of object with the input label
	 * @param  imagePlusInput ImagePlus
	 * @param  label double
	 * @return double volume
	 */
	public double computeVolumeObject(ImagePlus imagePlusInput, double label){
		Calibration calibration= imagePlusInput.getCalibration();
		double xCalibration = calibration.pixelWidth;
		double yCalibration = calibration.pixelHeight;
		double zCalibration = calibration.pixelDepth;
		double volume = 0;
		Histogram histogram = new Histogram ();
		histogram.run(imagePlusInput);
		HashMap<Double , Integer> hashMapHisto = histogram.getHistogram();
		volume =  hashMapHisto.get(label) *xCalibration*yCalibration*zCalibration;
		return volume;
	}
    
	/**
	 * method for the ERS
	 * 
	 * @param volume double
	 * @return double ERS
	 */
	public double equivalentSphericalRadius(double volume){
		double radius =  (3 * volume) / (4 * Math.PI);
		radius = Math.pow(radius, 1.0/3.0);
		return radius;
	}


	/**
	 * Method which compute the sphericity :
	 * 36Pi*Volume^2/Surface^3 = 1 if perfect sphere
	 * 
	 * @param volume
	 * @param surface
	 * @return
	 */
	public double computeSphericity(double volume, double surface)
	{
		return ((36 * Math.PI * (volume*volume)) / (surface*surface*surface));
	}
  
	/**
	 * Method which compute the eigen value of the matrix (differences between the
	 * coordinates of all points and the barycenter
	 * Obtaining a symmetric matrix :
	 * xx xy xz
	 * xy yy yz
	 * xz yz zz
	 * Compute the eigen value with the pakage JAMA
	 * 
	 * @param imagePlusInput
	 * @param label
	 * @return
	 */

	public double [] computeEigenValue3D(ImagePlus imagePlusInput, double label){
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		VoxelRecord barycenter = computeBarycenter3D (true,imagePlusInput,label);
		Calibration calibration= imagePlusInput.getCalibration();
		double xCalibration = calibration.pixelWidth;
		double yCalibration = calibration.pixelHeight;
		double zCalibration = calibration.pixelDepth;
		double xx = 0;
		double xy = 0;
		double xz = 0;
		double yy = 0;
		double yz = 0;
		double zz = 0;
		int compteur = 0;
		double voxelValue;
		for (int k = 0; k < imagePlusInput.getStackSize(); ++k){
			double dz = ((zCalibration * (double) k)-barycenter.getK());
			for (int i = 0; i < imagePlusInput.getWidth(); ++i){
				double dx = ((xCalibration * (double) i)-barycenter.getI());
				for (int j = 0; j < imagePlusInput.getHeight(); ++j){
					voxelValue = imageStackInput.getVoxel(i,j,k);
					if (voxelValue == label){ 
						double dy = ((yCalibration * (double) j)-barycenter.getJ());
						xx+= dx * dx;
						yy+= dy * dy;
						zz+= dz * dz;
						xy+= dx * dy;
						xz+= dx * dz;
						yz+= dy * dz;
						compteur++;
					}
				}
			}
		}
		double [][] tValues = {{xx / compteur, xy / compteur, xz / compteur},
                      {xy / compteur, yy / compteur, yz / compteur},
                      {xz / compteur, yz / compteur, zz / compteur}};
		Matrix matrix = new Matrix (tValues);
		EigenvalueDecomposition eigenValueDecomposition =  matrix.eig();
		return eigenValueDecomposition.getRealEigenvalues();
	}

  
	/**
	 * @param imagePlusInput
	 * @param label
	 * @return
	 */
	public double [] computeFlatnessAndElongation(ImagePlus imagePlusInput, double label){
		double [] shapeParameters = new double[2];
		double [] tEigenValues = computeEigenValue3D (imagePlusInput,label);
		shapeParameters [0] = Math.sqrt(tEigenValues[1] / tEigenValues[0]);
		shapeParameters [1] =  Math.sqrt (tEigenValues[2] / tEigenValues[1]);
		return shapeParameters;
	}  

	/**
	 * Method which determines object barycenter
	 * 
	 * @param unit if true the coordinates of barycenter are in µm.
	 * @param imagePlusInput
	 * @param label
	 * @return
	 */
	public VoxelRecord computeBarycenter3D(boolean unit,ImagePlus imagePlusInput, double label){
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		Calibration calibration= imagePlusInput.getCalibration();
		double xCalibration = calibration.pixelWidth;
		double yCalibration = calibration.pixelHeight;
		double zCalibration = calibration.pixelDepth;
		VoxelRecord voxelRecordBarycenter = new VoxelRecord ();
		int count = 0;
		int sx = 0;
		int sy = 0;
		int sz =0;
		double voxelValue;
		for (int k = 0; k < imagePlusInput.getStackSize(); ++k)
			for (int i = 0; i < imagePlusInput.getWidth(); ++i)
				for (int j = 0; j < imagePlusInput.getHeight(); ++j){
					voxelValue = imageStackInput.getVoxel(i,j,k);
					if (voxelValue == label ){
						sx +=i;
						sy +=j;
						sz +=k;
						++count;
					}
				}
		sx /= count;
		sy /= count;
		sz /= count;
		voxelRecordBarycenter.setLocation(sx, sy, sz);
		if (unit) voxelRecordBarycenter.Multiplie(xCalibration, yCalibration,zCalibration);
		return voxelRecordBarycenter;
  	}
	
	/**
	 * Method which compute the barycenter of each objects and return the result
	 * in a table of VoxelRecord
	 * 
	 * @param imagePlusInput
	 * @param unit
	 * @return
	 */
	public VoxelRecord[] computeObjectBarycenter(ImagePlus imagePlusInput, boolean unit){
		Histogram histogram = new Histogram();
		histogram.run(imagePlusInput);
		double []tlabel = histogram.getLabels();
		VoxelRecord [] tVoxelRecord = new VoxelRecord [ tlabel.length];
		for(int i = 0; i < tlabel.length; ++i)
			tVoxelRecord[i] = computeBarycenter3D(unit, imagePlusInput,tlabel[i] );
		return tVoxelRecord;
	}
	
	/**
	 * Intensity of chromocenters/ intensity of the nucleus
	 * 
	 * @param imagePlusInput
	 * @param imagePlusSegmented
	 * @param imagePlusChromocenter
	 * @return
	 */
	public double computeIntensityRHF(ImagePlus imagePlusInput, ImagePlus imagePlusSegmented, ImagePlus imagePlusChromocenter ){
		double chromocenterIntensity = 0;
	    double nucleusIntensity = 0;
	    double voxelValueChromocenter;
	    double voxelValueInput;
	    double voxelValueSegmented;
	    ImageStack imageStackChromocenter =  imagePlusChromocenter.getStack();
	    ImageStack imageStackSegmented = imagePlusSegmented.getStack();
	    ImageStack imageStackInput = imagePlusInput.getStack();
	    for (int k = 0; k < imagePlusInput.getNSlices(); ++k)
	    	for (int i = 0; i < imagePlusInput.getWidth(); ++i )
	    		for (int j = 0; j < imagePlusInput.getHeight(); ++j ){
	    			voxelValueSegmented = imageStackSegmented.getVoxel(i, j, k);
	    			voxelValueInput = imageStackInput.getVoxel(i, j, k);
	    			voxelValueChromocenter = imageStackChromocenter.getVoxel(i,j,k);  
	    			if (voxelValueSegmented > 0){
	    				if (voxelValueChromocenter > 0)
	    					chromocenterIntensity+=voxelValueInput;
	    				nucleusIntensity += voxelValueInput;
	    			}
	    		}
	    return chromocenterIntensity / nucleusIntensity;
	}
	
	/**
	 * intensity stat
	 * 0: min
	 * 1: max
	 * 2: avg
	 * 3: std
	 * 
	 * @param imagePlusInput
	 * @param imagePlusSegmented
	 * @param imagePlusChromocenter
	 * @return
	 */
	public ArrayList<Double> computeIntensityParameters(ImagePlus imagePlusInput, ImagePlus imagePlusSegmented){
		double min =3000;
		double max = 0;
		double sum = 0;
		int nbPixel = 0;
		double std = 0;
	    double smec =0;
	    ArrayList<Double> list= new ArrayList<Double>();
	    ImageStack imageStackSegmented = imagePlusSegmented.getStack();
	    ImageStack imageStackInput = imagePlusInput.getStack();
	    for (int k = 0; k < imagePlusInput.getNSlices(); ++k){
	    	for (int i = 0; i < imagePlusInput.getWidth(); ++i ){
	    		for (int j = 0; j < imagePlusInput.getHeight(); ++j){
	    			if (imageStackSegmented.getVoxel(i, j, k) > 0){
	    				double current = imageStackInput.getVoxel(i, j, k); 
	    				sum += current;
	    				if ( current < min) min = current;
	    				if ( current > max) max = current;
	    				nbPixel++;
	    			}
	    		}
	    	}
	    }
	    sum =sum/nbPixel;
	    for (int k = 0; k < imagePlusInput.getNSlices(); ++k){
	    	for (int i = 0; i < imagePlusInput.getWidth(); ++i ){
	    		for (int j = 0; j < imagePlusInput.getHeight(); ++j){
	    			if (imageStackSegmented.getVoxel(i, j, k) > 0) smec += (imageStackInput.getVoxel(i, j, k)-sum)*(imageStackInput.getVoxel(i, j, k)-sum); 
	    		}
	    	}
	    }
	    std = Math.sqrt(smec/(nbPixel-1));
	    list.add(min);
	    list.add(max);
	    list.add(sum);
	    list.add(std);
	    return list;
	}


	  /**
	   * Method which compute the RHF (total chromocenters volume / nucleus volume)
	   *
	   * @param imagePlusSegmented
	   * @param imagePlusChomocenters
	   * @return RHF
	   */
	  public double computeVolumeRHF(ImagePlus imagePlusSegmented, ImagePlus imagePlusChomocenters){
		  double volumeCc = 0;
		  double [] tVolumeChromocenter = computeVolumeofAllObjects(imagePlusChomocenters);
		  for (int i = 0; i < tVolumeChromocenter.length; ++i) 
			  volumeCc += tVolumeChromocenter[i];
		  double []tVolumeSegmented = computeVolumeofAllObjects(imagePlusSegmented);
		  return volumeCc / tVolumeSegmented[0];
	  } 
	  
	  /**
	   * Detect the number of object on segmented imaeg 
	   * @param imagePlusInput
	   * @return int nnbof object detected
	   */
	  public int getNumberOfObject (ImagePlus imagePlusInput){
		  Histogram histogram = new Histogram ();
		  histogram.run(imagePlusInput);
		  return histogram.getNbLabels();
	  } 
}