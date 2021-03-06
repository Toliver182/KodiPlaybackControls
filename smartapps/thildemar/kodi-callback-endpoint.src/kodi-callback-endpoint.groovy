/**
 *  KODI Callback Endpoint
 *
 *  Copyright 2015 Thildemar v0.012
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  To Use:  Publish Application "For Me", use xbmccallbacks2 plugin to point to individual command URLs for the following commands: play, stop, pause, resume
 *  Install Smartapp with desired switches and light levels.  Edit code below for each command as desired.
 *
 */
definition(
    name: "KODI Callback Endpoint",
    namespace: "Thildemar",
    author: "Thildemar",
    description: "Provides simple lighting control endpoint for KODI (XBMC) callbacks2 plugin.",
    category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment1-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment1-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Entertainment/entertainment1-icn@2x.png",
    oauth: true)

preferences {
	page(name: "pgSettings")
    page(name: "pgURL") 
}
//PAGES
///////////////////////////////
def pgSettings() {
    dynamicPage(name: "pgSettings", title: "Settings", uninstall: true) {
        section("Lights to Control") {
            input "switches", "capability.switch", required: true, title: "Which Switches?", multiple: true
        }
        section("Level to set Lights to (101 for last known level):") {
            input "playLevel", "number", required: true, title: "On Playback", defaultValue:"0"
            input "pauseLevel", "number", required: true, title: "On Pause", defaultValue:"40"
            input "resumeLevel", "number", required: true, title: "On Resume", defaultValue:"0"
            input "stopLevel", "number", required: true, title: "On Stop", defaultValue:"101"
        }
        section("Instance Preferences"){
        	label(name: "instanceLabel", title: "Label for this Instance", required: false, multiple: false)
        	//icon(title: "Icon for this Instance", required: false)
            input "onlyModes", "mode", title: "Only run in these modes", required: false, multiple: true
            input "neverModes", "mode", title: "Never run in these modes", required: false, multiple: true
        }
        section("View URLs"){
        	href( "pgURL", description: "Click here to view URLs", title: "")
        }
    }
}

def pgURL(){
    dynamicPage(name: "pgURL", title: "URLs", uninstall: false, install: false) {
    	if (!state.accessToken) {
        	createAccessToken() 
    	}
    	def url = apiServerUrl("/api/token/${state.accessToken}/smartapps/installations/${app.id}/")
    	section("Instructions") {
            paragraph "This app is designed to work with the xbmc.callbacks2 plugin for Kodi. Please download and install callbacks2 and in its settings assign the following URLs for corresponding events:"
            input "playvalue", "text", title:"Web address to copy for play command:", required: false, defaultValue:"${url}play"
            input "stopvalue", "text", title:"Web address to copy for stop command:", required: false, defaultValue:"${url}stop"
            input "pausevalue", "text", title:"Web address to copy for pause command:", required: false, defaultValue:"${url}pause"
            input "resumevalue", "text", title:"Web address to copy for resume command:", required: false, defaultValue:"${url}resume"
            paragraph "If you have more than one Kodi install, you may install an additional copy of this app for unique addresses specific to each room."
        }
    }
}
//END PAGES
/////////////////////////

mappings {

	path("/play") {
		action: [
			GET: "play"
		]
	}
	path("/stop") {
		action: [
			GET: "stop"
		]
	}
	path("/pause") {
		action: [
			GET: "pause"
		]
	}
    path("/resume") {
        action: [
            GET: "resume"
        ]
	}  
}

def installed() {}

def updated() {}

def uninstalled(){
	//Clean up Access Token
	revokeAccessToken()
	state.accessToken = null 
}



void play() {
	//Code to execute when playback started in KODI
    log.debug "Play command started"
    RunCommand(playLevel)
}
void stop() {
	//Code to execute when playback stopped in KODI
    log.debug "Stop command started"
    RunCommand(stopLevel)
}
void pause() {
	//Code to execute when playback paused in KODI
    log.debug "Pause command started"
    RunCommand(pauseLevel)
}
void resume() {
	//Code to execute when playback resumed in KODI
    log.debug "Resume command started"
    RunCommand(resumeLevel)
}

def deviceHandler(evt) {}

private void RunCommand(level){
	//Check to see if current mode is in white/black list before we do anything
	if((!onlyModes || location.currentMode in onlyModes) && !(location.currentMode in neverModes)){
    	//Mode is good, go ahead with commands
    	if (level == 101){
            log.debug "Restoring Last Known Light Levels"
            restoreLast(switches)
        }else if (level <= 100){
            log.debug "Setting lights to ${level} %"
            SetLight(switches,level)
    	}
    }
}

private void restoreLast(switchList){
	//This will look for the last external (not from this app) setting applied to each switch and set the switch back to that
	//Look at each switch passed
	switchList.each{sw ->
    	def lastState = LastState(sw) //get Last State
        if (lastState){   //As long as we found one, set it    
            SetLight(sw,lastState)
    	}else{ //Otherwise assume it was off
        	SetLight(sw, "off") 
        }
    }
}

private def LastState(device){
	//Get events for this device in the last day
	def devEvents = device.eventsSince(new Date() - 1, [max: 1000])
    //Find Last Event Where switch was turned on/off/level, but not changed by this app
    //Oddly not all events properly contain the "installedSmartAppId", particularly ones that actual contain useful values
    //In order to filter out events created by this app we have to find the set of events for the app control and the actual action
    //the first 8 char of the event ID seem to be unique much of the time, but the rest seems to be the same for any grouping of events, so match on that (substring)
    //In case the substring fails we will also check for events with similar timestamp (within 8 sec)
    def last = devEvents.find {
        (it.name == "level" || it.name == "switch") && (devEvents.find{it2 -> it2.installedSmartAppId == app.id && (it2.id.toString().substring(8) == it.id.toString().substring(8) || Math.sqrt((it2.date.getTime() - it.date.getTime())**2) < 6000 )} == null)
        }
    //If we found one return the stringValue
    if (last){
    	log.debug "Last External Event - Date: ${last.date} | Event ID: ${last.id} | AppID: ${last.installedSmartAppId} | Description: ${last.descriptionText} | Name: ${last.displayName} (${last.name}) | App: ${last.installedSmartApp} | Value: ${last.stringValue} | Source: ${last.source} | Desc: ${last.description}"
    	//if event is "on" find last externally set level as it could be in an older event
        if(last.stringValue == "on"){
        	devEvents = device.eventsSince(new Date() - 7, [max: 1000]) //Last level set command could have been awhile back, look in last 7 days
            def lastLevel = devEvents.find {
            (it.name == "level") && (devEvents.find{it2 -> it2.installedSmartAppId == app.id && (it2.id.toString().substring(8) == it.id.toString().substring(8) || Math.sqrt((it2.date.getTime() - it.date.getTime())**2) < 6000 )} == null)
            }
        	if(lastLevel){
            	return lastLevel.stringValue 
            }
        }
		return last.stringValue
    }else{
    	return null
    }
}

private void SetLight(switches,value){
	//Set value for one or more lights, translates dimmer values to on/off for basic switches

	//Fix any odd values that could be passed
	if(value.toString().isInteger()){
    	if(value.toInteger() < 0){value = 0}
        if(value.toInteger() > 100){value = 100}
    }else if(value.toString() != "on" && value.toString() != "off"){
    	return //ABORT! Lights do not support commands like "Hamster"
    }
	switches.each{sw ->
    	log.debug "${sw.name} |  ${value}"
    	if(value.toString() == "off" || value.toString() == "0"){ //0 and off are the same here, turn the light off
        	sw.off()
        }else if(value.toString() == "on"  || value.toString() == "100"){ //As stored light level is not really predictable, on should mean 100% for now
        	if(sw.hasCommand("setLevel")){ //setlevel for dimmers, on for basic
            	sw.setLevel(100)
            }else{
            	sw.on()
            }
        }else{ //Otherwise we should have a % value here after cleanup above, use ir or just turn a basic switch on
        	if(sw.hasCommand("setLevel")){//setlevel for dimmers, on for basic
            	sw.setLevel(value.toInteger())
            }else{
            	sw.on()
            }
        }
    }
}