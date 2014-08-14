/*
 * 
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.magnum.dataup.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.VideoFileManager;
import org.magnum.dataup.VideoSvcApi;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class VideoSvc{

	private ArrayList<Video> listVideos = new ArrayList<Video>();
	
	private static long videoId;
	
	/**
	 * Returns a list of the videos that have been added to the server. 
	 * The list is returned as JSON from the client.
	 * @return The collection of videos allocated in the server.
	 */
	@RequestMapping(value=VideoSvcApi.VIDEO_SVC_PATH, method=RequestMethod.GET)
	public @ResponseBody Collection<Video> getVideoList() {
		return listVideos;
	}
	
	
	/**
	 * Allows clients to add Video objects by sending POST requests
	 * that have an application/json body containing the Video object information. 
	 * @param v The video that the client sent to the server.
	 * @return The video with the id and the url. The client recives it in JSON format.
	 */
	@RequestMapping(value=VideoSvcApi.VIDEO_SVC_PATH, method=RequestMethod.POST)
	public @ResponseBody Video addVideo(@RequestBody Video v) {
		// We create the correct id for the video and his url
		videoId = listVideos.isEmpty() ? 1 : videoId++;
		String url = getDataUrl(videoId);
		
		// We set id and url for the video and save in the array
		v.setId(videoId);
		v.setDataUrl(url);
		listVideos.add(v);
		
		return v;
	}
	

	/**
	 * This method allows clients to set the mpeg video data for previously
	 * added Video objects by sending multipart POST requests to the server.
	 * The URL that the POST requests should be sent to includes the ID of the
	 * Video that the data should be associated with
	 * @param id The id of the video
	 * @param videoData The binary data of the video that the client want to upload to the server
	 * @param response The response that the server return to the client
	 * @return Return a <code>VideoStatus = VideoState.Ready</code> if there isn't any problem
	 * or return a response with the error code 404 to the client.
	 */
	@RequestMapping(value=VideoSvcApi.VIDEO_DATA_PATH, method=RequestMethod.POST)
	public @ResponseBody VideoStatus setVideoData(@PathVariable(VideoSvcApi.ID_PARAMETER) long id, 
			@RequestParam(VideoSvcApi.DATA_PARAMETER) MultipartFile videoData, HttpServletResponse response){
		
		// Check if there is a video with the id
		Video video = getVideo(id);
		if(video != null){	
			InputStream in;
			try {
				in = videoData.getInputStream();
				VideoFileManager.get().saveVideoData(video, in);
			} catch (IOException e) {
				e.printStackTrace();			
			}
		} else {
			response.setStatus(404);
			return null;
		}
		return new VideoStatus(VideoState.READY);	
	}


	/**
	 * This method should return the video data that has been associated with
	 * a Video object or a 404 if no video data has been set yet. Assumes that the 
	 * client knows the ID of the Video object that it would like to retrieve video
	 * data for.
	 * @param id The id of the video
	 * @param response The response that the server return to the client
	 */
	@RequestMapping(value=VideoSvcApi.VIDEO_DATA_PATH, method=RequestMethod.GET)
	public void getData(@PathVariable(VideoSvcApi.ID_PARAMETER) long id, HttpServletResponse response) {
		VideoFileManager videoFileManager;
	
		try {
			videoFileManager = VideoFileManager.get();
			
			// Prove if the server contains some video with the id, and it checks if there is
			// any data for that video
			Video v = getVideo(id);
			if(v != null && videoFileManager.hasVideoData(v)){
				OutputStream out = response.getOutputStream();
				videoFileManager.copyVideoData(v, out);
				response.setStatus(200);
			} else {				
				response.setStatus(404);
				return;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Return the video for the id param. If there isn't a video with the id,
	 * return null
	 * @param id The id of the video
	 * @return The video required or null
	 */
	private Video getVideo(long id){
		try{
			return listVideos.get((int) (id - 1));
		} catch(ArrayIndexOutOfBoundsException ex){
			return null;
		}
	}
	
	/**
	 * This method return the url from a video with the id indicated by the param
	 * @param videoId The id of the video that want obtain the url
	 * @return The url to the video with the id param
	 */
	private String getDataUrl(long videoId){
        String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
        return url;
    }

	/**
	 * This method return the name of the server in which is run the application
	 * @return The name of the server
	 */
    private String getUrlBaseForLocalServer() {
       HttpServletRequest request = 
           ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
       String base = 
          "http://"+request.getServerName() 
          + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
       return base;
    }
}
