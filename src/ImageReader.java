
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;	


public class ImageReader {

	JFrame frame;
	JLabel lbIm1;
	JLabel lbIm2;
	BufferedImage originalImage;
	BufferedImage processedImage;

	public enum Sample{
		SAMPLE_Y, SAMPLE_U, SAMPLE_V;		
	}

	//Inner classes to represent YUV and RGB
	class YUV {
		double y, u, v;
		public YUV(double y,double u, double v) {
			this.y = y;
			this.u = u;
			this.v = v;
		}
	}
	
	class RGB {
		int r, g, b;
		public RGB(int r, int g, int b){
			this.r = r;
			this.g= g;
			this.b = b;
		}
	}

	public void showIms(String[] args){
		int width = 352;  	//width : subsampling of YUV color space
		int height = 288; 	//height : Quantization of RGB values

		int input_Y = Integer.parseInt(args[1]);
		int input_U = Integer.parseInt(args[2]);
		int input_V = Integer.parseInt(args[3]);
		int Q = Integer.parseInt(args[4]);

		originalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		try {
			File file = new File(args[0]);	//input image
			InputStream is = new FileInputStream(file);

			long len = file.length();
			byte[] bytes = new byte[(int)len];

			int offset = 0;
			int numRead = 0;
			//Read input stream and store in bytes array
			while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length - offset)) >= 0) {	
				offset += numRead;
			}

			int index = 0;
			RGB[][] originalRGB = new RGB[height][width];
			RGB[][] modifiedRGB = new RGB[height][width];
			YUV[][] resultYUV = new YUV[height][width];

			for(int y = 0; y < height; y++) {
				for(int x = 0; x < width; x++) {

					int R = bytes[index];				    //R
					int G = bytes[index + height*width];	//G
					int B = bytes[index + height*width*2];  //B
					
					int pixel = 0xff000000 | ((R & 0xff) << 16) | ((G & 0xff) << 8) | (B & 0xff);					
					//int pixel = ((a << 24) + (r << 16) + (g << 8) + b);
					originalImage.setRGB(x,y,pixel);

					//convert to unsigned int
					R = R & 0xFF;
					G = G & 0xFF;
					B = B & 0xFF;

					//Store original RGB values to compare later
					RGB rgb = new RGB(R, G, B);
					originalRGB[y][x] = rgb;
					
					//2. Convert RGB to YUV
					double[] arrYUV = convertRBGtoYUV(R, G, B);	//get converted YUV for each pixel

					//3. Process YUV subsampling
					YUV objYUV = new YUV(arrYUV[0], arrYUV[1], arrYUV[2]);
					resultYUV[y][x] = objYUV;

					index++;
				}
			}


			//Do up-sampling
			for(int i = 0; i < height; i++) {
				for(int j = 0; j < width; j++) {

					//4) Adjust up-sampling for display - //1:2:2, 2:3:3, 5:3:3....it can be anything - 352,352,352
					if(input_Y !=0 && input_U != 0 && input_V != 0){
						resultYUV = upSample(resultYUV, input_Y, width, i, j, Sample.SAMPLE_Y);
						resultYUV = upSample(resultYUV, input_U, width, i, j, Sample.SAMPLE_U);
						resultYUV = upSample(resultYUV, input_V, width, i, j, Sample.SAMPLE_V);
					}

				}//inner for

			}//end outer for

			boolean doQuantization = true;
			Integer[] buckets = null;
			if(Q <= 256){
				double slotSize = 256/ (double) Q;
				//System.out.println("slotsize ="+slotSize);
				buckets = getBucketsForQuantization(slotSize);
			}else{
				doQuantization = false;
			}

			//Display
			for(int i = 0; i < height; i++) {
				for(int j = 0; j < width; j++) {

					YUV yuv = resultYUV[i][j];
					
					//5. Convert YUV to RGB
					int[] arrRGB = convertYUVtoRGB(yuv.y, yuv.u, yuv.v);
					int R = arrRGB[0];
					int G = arrRGB[1];
					int B = arrRGB[2];	

					//TODO: Quantization
					if(doQuantization) {
						int[] quantizedRGB = quantize(R, G, B, buckets);
						R = quantizedRGB[0];
						G = quantizedRGB[1];
						B = quantizedRGB[2];
					}					
					int processedPixel = 0xff000000 | ((R) << 16) | ((G) << 8) | (B);//0xff000000 | ((R & 0xff) << 16) | ((G & 0xff) << 8) | (B & 0xff);
					processedImage.setRGB(j, i, processedPixel);
					
					//Store Calculated RGB
					modifiedRGB[i][j] = new RGB(R, G, B);
					
				}
			}
			
			//For Anaysis Question 1 : Calculate MSE for different values of Y, U, V
			double MSE = 0;
			for(int i = 0; i < height; i++) {
				for(int j = 0; j < width; j++) {
					RGB rgb = originalRGB[i][j];
					RGB rgb_hat = modifiedRGB[i][j];
					
					MSE += (Math.pow((rgb.r - rgb_hat.r),2) + 
							Math.pow((rgb.g - rgb_hat.g),2) + 
								Math.pow((rgb.b - rgb_hat.b),2));	
				}
			}
			//System.out.println("\nFinal MSE ="+MSE);
			MSE = MSE / (352*288*3);
			double PSNR = 20* Math.log10(255) - 10*Math.log10(MSE);
			//System.out.println(PSNR);

			is.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Use labels to display the images
		frame = new JFrame();
		GridBagLayout gLayout = new GridBagLayout();
		frame.getContentPane().setLayout(gLayout);

		JLabel lbText1 = new JLabel("Original image (Left)");
		lbText1.setHorizontalAlignment(SwingConstants.CENTER);
		JLabel lbText2 = new JLabel("Image after modification (Right)");
		lbText2.setHorizontalAlignment(SwingConstants.CENTER);
		lbIm1 = new JLabel(new ImageIcon(originalImage));
		lbIm2 = new JLabel(new ImageIcon(processedImage));

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.CENTER;
		c.weightx = 0.5;
		c.gridx = 0;
		c.gridy = 0;
		frame.getContentPane().add(lbText1, c);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.CENTER;
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = 0;
		frame.getContentPane().add(lbText2, c);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 1;
		frame.getContentPane().add(lbIm1, c);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 1;
		c.gridy = 1;
		frame.getContentPane().add(lbIm2, c);

		frame.pack();
		frame.setVisible(true);


	}

	/**
	 * Upsampling with the input scheme. Using Mean/Weighted mean to fill the missing values to upsample
	 * @param resultYUV
	 * @param gap
	 * @param width
	 * @param i
	 * @param j
	 * @param sample
	 * @return
	 */
	private YUV[][] upSample(YUV[][] resultYUV, int gap, int width, int i, int j, Sample sample) {

		int k = j % gap;

		if(k != 0) {
			int prev = j-k;
			int next = j+gap-k; 

			if(next < width) {
				YUV prevYUV = resultYUV[i][prev];
				YUV currentYUV = resultYUV[i][j];
				YUV nextYUV = resultYUV[i][next];
				
				if(sample == Sample.SAMPLE_Y) {
					//currentYUV.y = (prevYUV.y + nextYUV.y)/2;
					currentYUV.y = ((gap - k)* prevYUV.y + (k * nextYUV.y))/gap;
				}else if(sample == Sample.SAMPLE_U) {
					//currentYUV.u = (prevYUV.u + nextYUV.u)/2;
					currentYUV.u = ((gap - k)* prevYUV.u + (k * nextYUV.u))/gap;
				}else if(sample == Sample.SAMPLE_V) {
					//currentYUV.v = (prevYUV.v + nextYUV.v)/2;
					currentYUV.v = ((gap - k)* prevYUV.v + (k * nextYUV.v))/gap;
				}				
			} else {
				//System.out.println("else-> prev = "+ prev + " next ="+next+" k="+k);
				YUV prevYUV = resultYUV[i][prev];

				for(int m = prev+1; m < width; m++) {
					YUV currentYUV = resultYUV[i][m];
					if(sample == Sample.SAMPLE_Y) {
						currentYUV.y = prevYUV.y;
					}else if(sample == Sample.SAMPLE_U) {
						currentYUV.u = prevYUV.u;
					}else if(sample == Sample.SAMPLE_V) {
						currentYUV.v = prevYUV.v;
					}
				}
			}

		}

		return resultYUV;

	}

	/**
	 * Calculate the quantization buckets as per step function
	 * @param step
	 * @return
	 */
	private Integer[] getBucketsForQuantization(double step) {
		LinkedList<Integer> list = new LinkedList<Integer>();
		double trueValue = 0;
		int value = 0;

		list.add(value);
		while(true){
			trueValue = trueValue + step;
			value = (int) Math.round(trueValue);

			if(value > 255){
				break;
			}
			list.add(value);
		}

		Integer[] buckets = new Integer[list.size()];
		buckets = list.toArray(buckets);

		return buckets;
	}

	/**
	 * Quantization Function to quantize the R,G,B values
	 * @param R
	 * @param G
	 * @param B
	 * @param buckets
	 * @return
	 */
	private int[] quantize(int R, int G, int B, Integer[] buckets) {

		for(int i=0; i < buckets.length-1; i++) {
			if(R >= buckets[i] && R <= buckets[i+1]){				
				int mean = (int) Math.round((buckets[i] + buckets[i+1])/(double)2);
				if(R < mean){
					R = buckets[i];
				}else{
					R = buckets[i+1];
				}
				break;
			}
		}
		if(R > 255){
			R = 255;
		}else if(R < 0){
			R = 0;
		}

		for(int i=0; i < buckets.length-1; i++){
			if(G >= buckets[i] && G <= buckets[i+1]){				
				int mean = (int) Math.round((buckets[i] + buckets[i+1])/(double)2);
				if(G < mean){
					G = buckets[i];
				}else{
					G = buckets[i+1];
				}
				break;
			}
		}
		if(G > 255){
			G = 255;
		}else if(G < 0){
			G = 0;
		}
		
		for(int i=0; i < buckets.length-1; i++){
			if(B >= buckets[i] && B <= buckets[i+1]){				
				int mean = (int) Math.round((buckets[i] + buckets[i+1])/(double)2);
				if(B < mean){
					B = buckets[i];
				}else{
					B = buckets[i+1];
				}
				break;
			}
		}
		if(B > 255){
			B = 255;
		}else if(B < 0){
			B = 0;
		}

		return new int[]{R, G, B};
	}

	/**
	 * Convert RGB to YUV
	 * @param R
	 * @param G
	 * @param B
	 * @return
	 */
	private double[] convertRBGtoYUV(int R, int G, int B) {
		double[] YUV = new double[3];

		YUV[0] = (0.299 * R + 0.587 * G + 0.114 * B);
		YUV[1] = (0.596 * R + (-0.274 * G) + (-0.322 * B));
		YUV[2] = (0.211 * R + (-0.523 * G) + (0.312 * B));

		return YUV;
	}

	/**
	 * Convert YUV to RGB
	 * @param Y
	 * @param U
	 * @param V
	 * @return
	 */
	private int[] convertYUVtoRGB(double Y, double U, double V) {
		int[] RGB = new int[3];

		RGB[0] = (int) (1.000 * Y + 0.956 * U + 0.621 * V);
		RGB[1] = (int) (1.000 * Y + (-0.272 * U) + (-0.647 * V));
		RGB[2] = (int) (1.000 * Y + (-1.106 * U) + (1.703 * V));

		return RGB;
	}



	public static void main(String[] args) {
		ImageReader ren = new ImageReader();
		ren.showIms(args);
	}
	

}