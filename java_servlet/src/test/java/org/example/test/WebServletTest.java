package org.example.test;


import java.io.FileWriter;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

class WebServletTest {
	public class profilingPerformance {
		public List<Long> latencies;

		public profilingPerformance() {
			latencies = new ArrayList<Long>();
		}

		public void addLatency(long latency) {
			latencies.add(latency);
		}

		public double getMean() {
			long totalTime = 0;
			for (Long latency : latencies) {
				totalTime += latency;
			}
			return totalTime / latencies.size();
		}

		public double getMedian() {
			latencies.sort(Comparator.naturalOrder());
			int length = latencies.size();
			if (length % 2 == 0) {

				return ((double) latencies.get(length / 2) + (double) latencies.get(length / 2 - 1)) / 2;
			} else {

				return (double) latencies.get(length / 2);
			}

		}

		public long getPercentile() {
			int index = (int) Math.ceil(0.99 * latencies.size());
			long p99 = latencies.get(index - 1);
			return p99;
		}
	}

	public class requestCounter {
		private int origin;
		public int[] threadCountOverClient;
		public int limitOfThreadsPerClient;
		public int numThreads;
		public int numClients;
		public List<Integer> subRange;

		public requestCounter(int start, int numThreads, int numClients,double error) {
			this.origin = start;

			this.numThreads = numThreads;
			this.threadCountOverClient = new int[numClients];
			this.limitOfThreadsPerClient = (int) Math.floor(numThreads / numClients);
			this.numClients = numClients;

			int errorRate = (int) Math.floor(this.numThreads * error);
			List<Integer> range = IntStream.rangeClosed(0, this.numThreads-1).boxed().toList();
			List<Integer> modifiableRange=new ArrayList<>(range);
			
			Collections.shuffle(modifiableRange);
			this.subRange = modifiableRange.subList(0, errorRate);

		}

		public int getValue() {
			return this.limitOfThreadsPerClient*this.numClients;
		}

		public boolean limitReached(int clientIdx) {
			if (this.threadCountOverClient[clientIdx] >= this.limitOfThreadsPerClient) {
				return true;
			}
			return false;
		}

		public void decrement(int clientIdx) {
			this.threadCountOverClient[clientIdx] -= 1;
		}

		public void increment(int clientIdx) {
			this.threadCountOverClient[clientIdx] += 1;
		}

		public int getBound() {
			return this.numThreads;
		}

		public List<Integer> getShuffledArray() {
			return this.subRange;
		}

	}

	public class requestStats {
		private long wallTime;

		public requestStats() {
			wallTime = 0;
		}

		public void setWallTime(long time) {
			wallTime += time;
		}

		public long getWallTime() {
			return wallTime;
		}

	}

	public class multithreadedPost {
		private int totalClients;
		private int minConnectionsPerClient;
		private int maxConnectionsPerClient;
		private requestCounter nReq;
		private requestStats stats = new requestStats();
		private boolean createCSV;
		private FileWriter writer;
		private String fileName = "post.csv";
		private profilingPerformance pperf;

		public multithreadedPost(int totalClients, int minCon, requestCounter nReq, boolean createCSV) {
			this.totalClients = totalClients;
			this.minConnectionsPerClient = minCon;

			this.nReq = nReq;
			this.createCSV = createCSV;
			pperf = new profilingPerformance();
			try {
				if (createCSV) {
					writer = new FileWriter(fileName);
					writer.append("Start Time, Request Type, Latency, Response Code\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		public void createMultipleClients() throws Exception {

			httpClient[] clients = new httpClient[this.totalClients];
			for (int i = 0; i < totalClients; i++) {
				clients[i] = new httpClient(i, minConnectionsPerClient, nReq, createCSV,
						writer, pperf);
			}
			long startTime = System.currentTimeMillis();
			for (httpClient client : clients) {
				client.start();
			}

			for (httpClient client : clients) {
				client.join();
			}

			stats.setWallTime(System.currentTimeMillis() - startTime);
			int sum = 0;
			for (httpClient client : clients) {
				// System.out.println("Client :"+j+" Completed:
				// "+clients[j].totalRequestsExecuted);
				sum += client.failed.size();
			}
			System.out.println("Failed Threads: " + sum);

			// ############ Performance Measure ###################
			System.out.printf("Wall Time %.4f sec%n", (float) stats.getWallTime() / 1000);
			int r = nReq.getValue();
			System.out.printf("Throughput %.4f r/sec%n", (float) ((float) r / stats.getWallTime() * 1000));
			System.out.println("Total Number of Requests " + r);

			// ############### Profiling Performance ###############//
			if (createCSV) {

				// Meand Time
				System.out.println("Mean Time " + pperf.getMean() * 1e-6 + " ms");

				// Median Time
				System.out.println("Median Time " + pperf.getMedian() * 1e-6);

				// 99th Percentile
				System.out.println("99th Percentile " + pperf.getPercentile() * 1e-6 + " ms");

				// Min-Max
				System.out.println("Min Response Time " + Collections.min(pperf.latencies) * 1e-6 + " ms");
				System.out.println("Max Response Time " + Collections.max(pperf.latencies) * 1e-6 + " ms");
			}

		}
	}

	public class httpClient extends Thread {
		private PoolingHttpClientConnectionManager httpCallablePool;
		private URI uri;
		private ConnectionKeepAliveStrategy myStrategy;
		private List<Integer> failed;
		private int minCon;
		private int maxCon;
		private int clientIdx;
		private requestCounter nReq;
		public int totalRequestsExecuted;
		private boolean createCSV;
		private FileWriter writer;
		private profilingPerformance pperf;

		public httpClient(int idx, int minCon, requestCounter nReq, boolean createCSV, FileWriter writer,
				profilingPerformance pperf) throws URISyntaxException {
			httpCallablePool = new PoolingHttpClientConnectionManager();
			httpCallablePool.setMaxTotal(minCon);
			httpCallablePool.setDefaultMaxPerRoute(minCon);// Since we have only one root
			this.nReq = nReq;
			this.minCon = minCon;
			this.maxCon = nReq.limitOfThreadsPerClient;
			this.writer = writer;
			this.pperf = pperf;
			this.clientIdx = idx;
			totalRequestsExecuted = 0;
			this.createCSV = createCSV;
			failed = new ArrayList<Integer>();
			uri = new URIBuilder()
					.setScheme("http")
					.setHost("localhost:8080/skiresorts")
					.setPath("/Skiers")
					.build();

			myStrategy = new ConnectionKeepAliveStrategy() {
				@Override
				public long getKeepAliveDuration(HttpResponse response,
						HttpContext context) {
					// Time to keep the connection Alive to reuse.
					return 1000;

				}
			};

		}

		@Override
		public void run() {
			CloseableHttpClient httpClient = HttpClients.custom()
					.setKeepAliveStrategy(myStrategy)
					.setServiceUnavailableRetryStrategy(new ServiceUnavailableRetryStrategy() {
						@Override
						public boolean retryRequest(
								final HttpResponse response, final int executionCount, final HttpContext context) {
							int statusCode = response.getStatusLine().getStatusCode();
							
							return (statusCode >= 400 || statusCode >= 500) && executionCount < 5;
						}

						@Override
						public long getRetryInterval() {
							return 0;
						}
					})
					.setConnectionManager(httpCallablePool).build();

			try {
				HttpThread[] httpPosts = new HttpThread[this.maxCon];
				
				for (int i = 0; i < httpPosts.length; i++) {
					int packetNo=clientIdx*nReq.limitOfThreadsPerClient + i;
					httpPosts[i] = new HttpThread(packetNo, httpClient, uri, createCSV, writer, pperf, false);
					
					
					// synchronized (nReq) {

					// nReq.increment(clientIdx);
					// if (nReq.limitReached(clientIdx)) {
					// break;
					// }

					// }
				}
				int r1=clientIdx*nReq.limitOfThreadsPerClient;
				int r2=(clientIdx+1)*nReq.limitOfThreadsPerClient;
				boolean descale=r1>=nReq.limitOfThreadsPerClient?true:false;
				for(int errorIndx:nReq.getShuffledArray()){
					if(errorIndx>=r1 && errorIndx<r2){
						if(descale){
							int idx=errorIndx - r1;
						
							httpPosts[idx].iE=true;
						}else{
							httpPosts[errorIndx].iE=true;
						}
					
					}
				}
				for (int i = 0; i < httpPosts.length; i++) {
					httpPosts[i].start();
				}

				for (int j = 0; j < httpPosts.length; j++) {
					

					httpPosts[j].join();

					if (!(httpPosts[j].status >= 200 && httpPosts[j].status < 300)) {
						this.failed.add(j);// add thread index to the failed list of client
					} else {
						// System.out.println("Client: "+clientIdx+", Thread: "+j+", Done");
					}

				}
				httpClient.close();

			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public class HttpThread extends Thread {
		private URI uri;
		private CloseableHttpClient client;
		private Gson gson;
		public Integer status;
		private boolean createCSV;
		private FileWriter writer;
		private profilingPerformance pperf;
		public boolean iE;
		private int tID;

		public HttpThread(int tID, CloseableHttpClient client, URI uri, boolean createCSV, FileWriter writer,
				profilingPerformance pperf,boolean injectException) {
			this.uri = uri;
			this.client = client;
			this.createCSV = createCSV;
			this.writer = writer;
			this.pperf = pperf;
			this.iE=injectException;
			this.tID = tID;

			gson = new Gson();
		}

		@Override
		public void run() {
			// Each thread will generate its own data
			// String threadID = String.valueOf(cID) + String.valueOf(tID);
			SkierLiftRideEvent data = new SkierLiftRideEvent(2022, 1, this.tID,this.iE);
			try {
				StringEntity body = new StringEntity(gson.toJson(data));

				HttpPost request = new HttpPost(this.uri);
				request.setEntity(body);

				// long st=System.currentTimeMillis();
				long st = System.nanoTime();
				LocalTime lst = LocalTime.now();

				CloseableHttpResponse response = this.client.execute(request);

				long latency = System.nanoTime() - st;
				this.status = response.getStatusLine().getStatusCode();
				if (createCSV) {
					synchronized (writer) {

						writer.append(String.valueOf(lst) + "," + "POST" + "," + String.valueOf(latency) +
								"," + String.valueOf(this.status) + "\n");
						pperf.addLatency(latency);
					}

				}

				try {
					HttpEntity entity = response.getEntity();

					if (entity != null) {
						EntityUtils.consume(entity);
					}
				} finally {
					response.close();
				}

			} catch (Exception e) {
				System.out.println("Thread Failed: " + e);
				// return 0;
			}
		}

	}

	public class SkierLiftRideEvent {
		private int skierID;
		private int resortID;
		private int liftID;
		private int seasonID;
		private int dayID;
		private int time;
		private int packetID;
		private boolean injectException;
		// private int limitOfThreadsPerClient;
		// private int numThreads;
		// private int numClients;

		public SkierLiftRideEvent(int seasonID, int dayID, int pID,boolean injectException) {
			this.seasonID = seasonID;
			this.dayID = dayID;
			this.packetID = pID;
			Random rand = new Random();
			this.skierID = rand.nextInt(1, 100000);
			this.resortID = rand.nextInt(1, 10);
			this.liftID = rand.nextInt(1, 40);
			this.time = rand.nextInt(1, 360);
			this.injectException=injectException;
			// this.numThreads = numThreads;
			// this.limitOfThreadsPerClient = threadsPerClient;
			// this.numClients = numClients;
		}

	}

	public class MyException extends Exception {
		public MyException(String message, Throwable err) {
			super(message, err);
		}
	}

	ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
		@Override
		public String handleResponse(org.apache.http.HttpResponse response)
				throws ClientProtocolException, IOException {
			int status = response.getStatusLine().getStatusCode();
			if (status >= 200 && status < 300) {
				HttpEntity entity = response.getEntity();
				return entity != null ? EntityUtils.toString(entity) : null;
			} else {
				HttpEntity entity = response.getEntity();
				String message = EntityUtils.toString(entity);

				return message + "Status: " + status;

			}
		}
	};

	@Test
	void testSkiersGet() throws Exception {

		URI uri = new URIBuilder()
				.setScheme("http")
				.setHost("localhost:8080/skiresorts")
				.setPath("/Skiers")
				.build();

		HttpGet request = new HttpGet(uri);
		CloseableHttpClient client = HttpClients.createDefault();
		// --------------- Response Handler------------------
		String response = client.execute(request, responseHandler);

		System.out.println("Get Response " + response);

		client.close();

	}

	

	@SuppressWarnings("deprecation")
	// @Test
	// void testSkiersPost() throws Exception {
	// requestCounter noRequests=new requestCounter(0,10000);
	// multithreadedPost mp=new multithreadedPost(15,5,1000,noRequests,true);
	// mp.createMultipleClients();

	// }

	@Test
	void testSkiersPostLatency() throws Exception {
		System.out.println("######## Testing Latency #########");
		int num_of_clients = 2;
		int num_of_threads = 5000;
		double error=0.1;
		requestCounter noRequests = new requestCounter(0, num_of_threads, num_of_clients,error);
		
		multithreadedPost mp = new multithreadedPost(num_of_clients, 2,
				noRequests, false);
		mp.createMultipleClients();
		

		
		// timePerRequest=(float)(mp.stats.getWallTime()/(float)noRequests.getValue());
		// System.out.printf("Time for each request %.4f ms%n",timePerRequest);
		// //System.out.printf("Number of Workers required for %d requests, by Little's
		// Law: %f%n",10000,(10000 * timePerRequest)/1000);

		// requestCounter noRequests2=new requestCounter(0,10000);
		// multithreadedPost mp2=new multithreadedPost(2,1,3000,noRequests2,false);
		// mp2.createMultipleClients();
		
		// estThroughput=(float)((float)noRequests2.getValue()/mp2.stats.getWallTime());
		// System.out.printf("Estimated Throughput by Litlle's Law, by running %d is
		// %.4f ms%n",noRequests2.getValue(),estThroughput*1000);

	}
	

	// @Test
	// void registerPostPerformance() throws Exception{
	// requestCounter noRequests=new requestCounter(0,100);
	// multithreadedPost mp=new multithreadedPost(5,5,100,noRequests,false);
	// mp.createMultipleClients();
	// }

}
