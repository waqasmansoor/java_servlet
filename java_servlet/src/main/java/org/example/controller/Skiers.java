package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


import com.google.gson.Gson;

@WebServlet(name = "Skiers", value = "Skiers")
public class Skiers extends HttpServlet {
    Map<Integer, Integer> faultyPackets = new HashMap<Integer, Integer>();

    public class SkierLiftRideEvent {
        public int skierID;
        public int resortID;
        public int liftID;
        public int seasonID;
        public int dayID;
        public int time;
        public int packetID;
        public boolean injectException;
        // Dummy Data

        // public int errorRate=(int)Math.floor(nRequests*0.15);
        // List<Integer> range=IntStream.rangeClosed(0, nRequests).boxed().toList();
        // Collections.shuffle(range);
        // List<Integer> subRange=range.subList(0, errorRate);

        private int processRequest(HttpServletResponse response,Map<Integer,Integer> fp) throws ServletException, IOException {
            // PrintWriter out = response.getWriter();

            // ############ TESTING RETRY ################//

            if (injectException) {
                
                if (fp.getOrDefault(packetID, 0) == 4) {
                    return HttpServletResponse.SC_OK;
                }
                return HttpServletResponse.SC_BAD_REQUEST;
                // response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }

            if (!(skierID >= 1 && skierID < 100000) ||
                    !(resortID >= 1 && resortID < 10) ||
                    !(liftID >= 1 && liftID < 40) ||
                    !(seasonID == 2022) ||
                    !(dayID == 1) ||
                    !(time >= 1 && time < 360)) {
                // response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                // out.write("Failed!, Please Check Parameters...");
                return HttpServletResponse.SC_BAD_REQUEST;

            }
            // ############### Set Resoponse #################/

            else {
                return HttpServletResponse.SC_OK;
                // out.write("Post Success!!!");
            }

        }

    }

    protected synchronized void savePackets(int id, Map<Integer, Integer> fp) {

        
        fp.put(id, fp.getOrDefault(id, 0) + 1);
        System.out.printf("Task %d skipped %dtimes\n",id,fp.getOrDefault(id, 0));
        


    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.getOutputStream().print("Success!!!");

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        Gson gson = new Gson();
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = request.getReader().readLine()) != null) {
            sb.append(s);
        }

        SkierLiftRideEvent se = (SkierLiftRideEvent) gson.fromJson(sb.toString(), SkierLiftRideEvent.class);

        int resp = se.processRequest(response,faultyPackets);
        if (resp != 200) {
            savePackets(se.packetID, faultyPackets);
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

        }

    }

}
