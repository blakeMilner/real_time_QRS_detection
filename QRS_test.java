import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


public class QRS_test {

    public static final int M = 5;
    public static final int N = 30;
    public static final int winSize = 250;
    public static final float HP_CONSTANT = (float) 1/M;


	public static void main(String[] args) throws IOException {

		ArrayList<String> data_list = readCSVToArrayList("data.csv");

		int nsamp = data_list.size()-2;
		float[] ecg = new float[nsamp];
		for(int i=2;i<nsamp;i++){
			ecg[i-2] = Float.parseFloat(data_list.get(i));
		}
		
		int[] QRS = detect(ecg);


        try {
        	FileWriter fw = new FileWriter( "QRS.csv",true);
        	BufferedWriter bw=new BufferedWriter(fw);

        	for (int i = 0; i < QRS.length; i++) {
                                bw.write(String.valueOf(QRS[i]) );
                                bw.newLine();
        	}
        	bw.close();

        } catch (IOException e) {
			e.printStackTrace();
			}

    }

	 public static ArrayList<String> readCSVToArrayList(String csvpath) {

	        ArrayList<String> dataAL = new ArrayList<String>();

	        BufferedReader reader;
	        try {
	            reader = new BufferedReader(new FileReader(csvpath));
	            //reader.readLine();//
	            String line = null;//

                int line_num = 0;
	            while ((line = reader.readLine()) != null) {

	                String item[] = line.split(",");
	                dataAL.add(item[0]);

	                //System.out.println(dataAL.get(line_num));
	                line_num++;
	            }
	            //System.out.println(dataAL.size());
	            //System.out.print(ticketStr.toString());

	        } catch (FileNotFoundException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        } catch (IOException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        }

	        return dataAL;
	    }

		public static int[] detect(float[] ecg) {
			// circular buffer for input ecg signal
			// we need to keep a history of M + 1 samples for HP filter
			float[] ecg_circ_buff = new float[M + 1];
			int ecg_circ_WR_idx = 0;
			int ecg_circ_RD_idx = 0;
		
			// circular buffer for input ecg signal
			// we need to keep a history of N+1 samples for LP filter
			float[] hp_circ_buff = new float[N+1];
			int hp_circ_WR_idx = 0;
			int hp_circ_RD_idx = 0;		
		
			// LP filter outputs a single point for every input point
			// This goes straight to adaptive filtering for eval
			float next_eval_pt = 0;
			
			// output 
			int[] QRS = new int[ecg.length];
			
			// running sums for HP and LP filters, values shifted in FILO
			float hp_sum = 0;
            float lp_sum = 0;
	        
	        // parameters for adaptive thresholding
			double treshold = 0;
			boolean triggered = false;
			int trig_time = 0;
			float win_max = 0;
			int win_idx = 0;
				
			for(int i = 0; i < ecg.length; i++){
				ecg_circ_buff[ecg_circ_WR_idx++] = ecg[i];
				ecg_circ_WR_idx %= (M+1);
				
				/* High pass filtering */
				if(i < M){
					// first fill buffer with enough points for HP filter
					hp_sum += ecg_circ_buff[ecg_circ_RD_idx];
					hp_circ_buff[hp_circ_WR_idx] = 0;
				}
				else{
					hp_sum += ecg_circ_buff[ecg_circ_RD_idx];
					
					int tmp = ecg_circ_RD_idx - M;
					if(tmp < 0){
						tmp += M + 1;
					}
					hp_sum -= ecg_circ_buff[tmp];
					
					float y1 = 0;
					float y2 = 0;

					tmp = (ecg_circ_RD_idx - ((M+1)/2));
					if(tmp < 0){
						tmp += M + 1;
					}
					y2 = ecg_circ_buff[tmp];

					y1 = HP_CONSTANT * hp_sum; 
					
					hp_circ_buff[hp_circ_WR_idx] = y2 - y1;
				}
				
				ecg_circ_RD_idx++;
				ecg_circ_RD_idx %= (M+1);
				
				hp_circ_WR_idx++;
				hp_circ_WR_idx %= (N+1);
					
				/* Low pass filtering */
				
				// shift in new sample from high pass filter
				lp_sum += hp_circ_buff[hp_circ_RD_idx] * hp_circ_buff[hp_circ_RD_idx];
				
				if(i < N){
					// first fill buffer with enough points for LP filter
					next_eval_pt = 0;
					
				}
				else{
					// shift out oldest data point
					int tmp = hp_circ_RD_idx - N;
					if(tmp < 0){
						tmp += N+1;
					}					
					lp_sum -= hp_circ_buff[tmp] * hp_circ_buff[tmp];
					
					next_eval_pt = lp_sum;
				}
				
				hp_circ_RD_idx++;
				hp_circ_RD_idx %= (N+1);
				
				/* Adapative thresholding beat detection */
				// set initial threshold				
				if(i < winSize) {
					if(next_eval_pt > treshold) {
						treshold = next_eval_pt;
					}
				}
	        
				// check if detection hold off period has passed
				if(triggered){
					trig_time++;
				
					if(trig_time >= 100){
						triggered = false;
						trig_time = 0;
					}
				}

				// find if we have a new max
				if(next_eval_pt > win_max) win_max = next_eval_pt;

				// find if we are above adaptive threshold
	            if(next_eval_pt > treshold && !triggered) {
					QRS[i] = 1;

					triggered = true;
	            }
	            else {
					QRS[i] = 0;
	            }
	            
				// adjust adaptive threshold using max of signal found 
				// in previous window            
		    	if(++win_idx > winSize){
					// weighting factor for determining the contribution of
					// the current peak value to the threshold adjustment
		        	double gamma = 0.175;
		        	
		        	// forgetting factor - 
		        	// rate at which we forget old observations
					double alpha = 0.01 + (Math.random() * ((0.1 - 0.01)));
					
					treshold = alpha * gamma * win_max + (1 - alpha) * treshold;
			
					// reset current window ind
					win_idx = 0;
					win_max = -10000000;
	            }
			}

			return QRS;
		}
}
