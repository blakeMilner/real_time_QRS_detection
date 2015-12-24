#include <stdio.h>
#include <stdlib.h>


typedef int boolean;
#define true  1
#define false 0

#define M 				5
#define N 				30
#define winSize			250
#define HP_CONSTANT		((float) 1 / (float) M)

void detect(float* ecg, int* result, int len);

int main(int argc, char** argv){
	float new_pt;
	float ecg[1000000] = {0};
	int result[1000000] = {0};
	int i, j;
	
	// read in ECG data
	FILE *fid = NULL;
	
	if(argc > 1){
		fid = fopen(argv[1], "r");
	}
	else{
		fid = fopen("data.csv", "r");
	}
	
	
	while( EOF != fscanf(fid, "%f\n", &new_pt) ){
		ecg[i++] = new_pt;
		
//		printf("%f\n", new_pt);
	}
	
	fclose(fid);
	
	// perform realtime QRS detection
	detect(ecg, result, i);
	
	// save detection results
	fid = fopen("QRS.csv", "w");
	
	for(j = 0; j < i; j++){
		fprintf(fid, "%d\n", result[j]);
	}
	
	fclose(fid);
	
	return 0;
}

void detect(float* ecg, int* result, int len) {
	// circular buffer for input ecg signal
	// we need to keep a history of M + 1 samples for HP filter
	float ecg_buff[M + 1] = {0};
	int ecg_buff_WR_idx = 0;
	int ecg_buff_RD_idx = 0;
	
	// circular buffer for input ecg signal
	// we need to keep a history of N+1 samples for LP filter
	float hp_buff[N + 1] = {0};
	int hp_buff_WR_idx = 0;
	int hp_buff_RD_idx = 0;
	
	// LP filter outputs a single point for every input point
	// This goes straight to adaptive filtering for eval
	float next_eval_pt = 0;
	
	// running sums for HP and LP filters, values shifted in FILO
	float hp_sum = 0;
	float lp_sum = 0;
	
	// parameters for adaptive thresholding
	double treshold = 0;
	boolean triggered = false;
	int trig_time = 0;
	float win_max = 0;
	int win_idx = 0;
	
	int i = 0;
	
	for(i = 0; i < len; i++){
		ecg_buff[ecg_buff_WR_idx++] = ecg[i];
		ecg_buff_WR_idx %= (M+1);
		
		//printf("i - %d\n", i);
		
		/* High pass filtering */
		if(i < M){
			// first fill buffer with enough points for HP filter
			hp_sum += ecg_buff[ecg_buff_RD_idx];
			hp_buff[hp_buff_WR_idx] = 0;
			
			//printf("hp_buff[hp_buff_WR_idx] - %f\n", hp_buff[hp_buff_WR_idx]);
		}
		else{
			hp_sum += ecg_buff[ecg_buff_RD_idx];
			
			int tmp = ecg_buff_RD_idx - M;
			if(tmp < 0) tmp += M + 1;
			
			hp_sum -= ecg_buff[tmp];
			
			float y1 = 0;
			float y2 = 0;
			
			tmp = (ecg_buff_RD_idx - ((M+1)/2));
			if(tmp < 0) tmp += M + 1;
			
			y2 = ecg_buff[tmp];
			
			y1 = HP_CONSTANT * hp_sum;
			
			hp_buff[hp_buff_WR_idx] = y2 - y1;
			
			//printf("hp_buff[hp_buff_WR_idx] - %f\n", hp_buff[hp_buff_WR_idx]);
		}
		
		// done reading ECG buffer, increment position
		ecg_buff_RD_idx++;
		ecg_buff_RD_idx %= (M+1);
		
		// done writing to HP buffer, increment position
		hp_buff_WR_idx++;
		hp_buff_WR_idx %= (N+1);
		
		/* Low pass filtering */
		
		// shift in new sample from high pass filter
		lp_sum += hp_buff[hp_buff_RD_idx] * hp_buff[hp_buff_RD_idx];
		
		if(i < N){
			// first fill buffer with enough points for LP filter
			next_eval_pt = 0;
			
		}
		else{
			// shift out oldest data point
			int tmp = hp_buff_RD_idx - N;
			if(tmp < 0) tmp += N+1;
			
			lp_sum -= hp_buff[tmp] * hp_buff[tmp];
			
			next_eval_pt = lp_sum;
		}
		
		// done reading HP buffer, increment position
		hp_buff_RD_idx++;
		hp_buff_RD_idx %= (N+1);
		
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
			result[i] = 1;
			
			triggered = true;
		}
		else {
			result[i] = 0;
		}
		
		// adjust adaptive threshold using max of signal found 
		// in previous window            
		if(win_idx++ >= winSize){
			// weighting factor for determining the contribution of
			// the current peak value to the threshold adjustment
			double gamma = 0.175;
			
			// forgetting factor - 
			// rate at which we forget old observations
			double alpha = 0.01 + ( ((float) rand() / (float) RAND_MAX) * ((0.1 - 0.01)));
			
			treshold = alpha * gamma * win_max + (1 - alpha) * treshold;
			
			// reset current window ind
			win_idx = 0;
			win_max = -10000000;
		}
	}
}
