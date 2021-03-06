/*
 * Copyright (c) 2018 Joerg Bayer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.sg_o.app.miio.serverTest;

import de.sg_o.app.miio.base.CommandExecutionException;
import de.sg_o.app.miio.vacuum.*;
import org.joda.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;
import de.sg_o.app.miio.server.OnServerEventListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;


public class ServerVacuumEvents implements OnServerEventListener {
    private VacuumStatus state = new VacuumStatus(null);
    private VacuumConsumableStatus consumables = new VacuumConsumableStatus(null);
    private TimeZone timezone = TimeZone.getDefault();
    private Map<String, VacuumTimer> timers = new LinkedHashMap<>();
    private VacuumDoNotDisturb dnd = new VacuumDoNotDisturb(null, null);
    private Map<Long, VacuumCleanup> cleanups = new LinkedHashMap<>();
    private int soundVolume = 90;
    private VacuumSounpackInstallState soundSetupState = new VacuumSounpackInstallState(0, VacuumSounpackInstallState.State.UNKNOWN, VacuumSounpackInstallState.Error.NONE, 0);
    private JSONObject carpedMode = new JSONObject("{\"current_high\":500,\"stall_time\":10,\"current_low\":400,\"enable\":0,\"current_integral\":450}");
    private JSONObject serialNumber = new JSONObject("{\"serial_number\":\"0000000000001\"}");

    public ServerVacuumEvents() {
    }

    public VacuumConsumableStatus getConsumables() {
        return consumables;
    }

    @Override
    public Object onCommandListener(String method, Object params) {
        JSONArray paramsArray = null;
        JSONObject paramsObject = null;
        if (params != null){
            if (params.getClass() == JSONArray.class){
                paramsArray = (JSONArray) params;
            }
            if (params.getClass() == JSONObject.class){
                //noinspection ConstantConditions
                paramsObject = (JSONObject) params;
            }
        }
        switch (method){
            case "get_status":
                return status();
            case "get_timezone":
                return getTimezone();
            case "set_timezone":
                return setTimezone(paramsArray);
            case "get_consumable":
                return consumableStatus();
            case "reset_consumable":
                return resetConsumableStatus(paramsArray);
            case "get_custom_mode":
                return getFanSpeed();
            case "set_custom_mode":
                return setFanSpeed(paramsArray);
            case "app_start":
                return start();
            case "app_pause":
                return pause();
            case "app_stop":
                return stop();
            case "app_charge":
                return home();
            case "app_spot":
                return spotCleaning();
            case "find_me":
                return findMe();
            case "get_timer":
                return getTimers();
            case "set_timer":
                return addTimer(paramsArray);
            case "upd_timer":
                return setTimerEnabled(paramsArray);
            case "del_timer":
                return removeTimer(paramsArray);
            case "get_dnd_timer":
                return getDoNotDisturb();
            case "set_dnd_timer":
                return setDoNotDisturb(paramsArray);
            case "close_dnd_timer":
                return disableDoNotDisturb();
            case "app_goto_target":
                return goTo(paramsArray);
            case "app_zoned_clean":
                return cleanArea(paramsArray);
            case "get_clean_summary":
                return getCleaningSummary();
            case "get_clean_record":
                return getCleanup(paramsArray);
            case "get_sound_volume":
                return getSoundVolume();
            case "change_sound_volume":
                return setSoundVolume(paramsArray);
            case "test_sound_volume":
                return testSoundVolume();
            case "app_rc_start":
                return manualControlStart();
            case "app_rc_end":
                return manualControlStop();
            case "app_rc_move":
                return manualControlMove(paramsArray);
            case "dnld_install_sound":
                return installSoundpack(paramsObject);
            case "get_sound_progress":
                return soundpackInstallStatus();
            case "get_carpet_mode":
                return getCarpetModeState();
            case "set_carpet_mode":
                return setCarpetMode(paramsArray);
            case "get_serial_number":
                return getSerialnumber();
            default:
                return null;
        }
    }

    private Object status(){
        JSONArray ret = new JSONArray();
        ret.put(state.construct());
        return ret;
    }

    private Object getTimezone(){
        JSONArray ret = new JSONArray();
        ret.put(timezone.getID());
        return ret;
    }

    private Object setTimezone(JSONArray tz){
        if(tz == null) return null;
        String zone = tz.optString(0, null);
        if (zone == null)  return null;
        this.timezone = TimeZone.getTimeZone(zone);
        return ok();
    }

    private Object consumableStatus(){
        JSONArray ret = new JSONArray();
        ret.put(consumables.construct());
        return ret;
    }

    private Object resetConsumableStatus(JSONArray params){
        if (params == null) return null;
        String name = params.optString(0, "");
        consumables.reset(name);
        return ok();
    }

    private Object getFanSpeed(){
        JSONArray ret = new JSONArray();
        ret.put(state.getFanPower());
        return ret;
    }

    private Object setFanSpeed(JSONArray params){
        if (params == null) return null;
        int speed = params.optInt(0, -1);
        if ((speed < 0) || (speed > 100)) return null;
        state.setFanPower(speed);
        return ok();
    }

    private Object start()  {
        state.setState(VacuumStatus.State.CLEANING);
        Instant start = Instant.now();
        cleanups.put((long) cleanups.size(), new VacuumCleanup(start, start.plus(200000), 200, 30000, true));
        return ok();
    }

    private Object pause() {
        state.setState(VacuumStatus.State.PAUSED);
        return ok();
    }

    private Object stop() {
        state.setState(VacuumStatus.State.IDLE);
        return ok();
    }

    private Object home() {
        state.setState(VacuumStatus.State.CHARGING);
        return ok();
    }

    private Object spotCleaning() {
        state.setState(VacuumStatus.State.SPOT_CLEANUP);
        Instant start = Instant.now();
        cleanups.put((long) cleanups.size(), new VacuumCleanup(start, start.plus(100000), 100, 10000, true));
        return ok();
    }

    private Object findMe() {
        return ok();
    }

    private Object getTimers(){
        JSONArray resp = new JSONArray();
        for (VacuumTimer t : timers.values()) {
            resp.put(t.construct(true));
        }
        return resp;
    }

    private Object addTimer(JSONArray timer) {
        if (timer == null) return null;
        JSONArray t = timer.optJSONArray(0);
        if (t == null) return null;
        try {
            VacuumTimer tm = new VacuumTimer(t);
            JSONArray job = new JSONArray();
            job.put("start_clean");
            job.put(-1);
            tm.setJob(job);
            timers.put(tm.getID(), tm);
        } catch (CommandExecutionException e) {
            return null;
        }
        return ok();
    }

    private Object setTimerEnabled(JSONArray timer){
        if (timer == null) return null;
        VacuumTimer t = timers.get(timer.optString(0));
        if (t == null) return null;
        t.setEnabled(timer.optString(1).equals("on"));
        return ok();
    }

    private Object removeTimer(JSONArray timer){
        if (timer == null) return null;
        VacuumTimer t = timers.remove(timer.optString(0));
        if (t == null) return null;
        return ok();
    }

    private Object getDoNotDisturb(){
        return dnd.construct(true);
    }

    private Object setDoNotDisturb(JSONArray doNotDisturb){
        if (doNotDisturb == null) return null;
        dnd = new VacuumDoNotDisturb(doNotDisturb);
        state.setDndEnabled(true);
        return ok();
    }

    private Object disableDoNotDisturb(){
        dnd.setEnabled(false);
        state.setDndEnabled(false);
        return ok();
    }

    private Object goTo(JSONArray p) {
        if (p == null) return null;
        state.setState(VacuumStatus.State.IDLE);
        return ok();
    }

    private Object cleanArea(JSONArray values) {
        if (values == null) return null;
        Instant start = Instant.now();
        cleanups.put((long) cleanups.size(), new VacuumCleanup(start, start.plus(150000), 150, 20000, true));
        state.setState(VacuumStatus.State.CLEANING_ZONE);
        return ok();
    }

    private Object getCleaningSummary(){
        JSONArray cleans = new JSONArray();
        for (Long id : cleanups.keySet()){
            cleans.put(id.longValue());
        }
        long runtime = 0;
        long area = 0;
        for (VacuumCleanup c : cleanups.values()){
            runtime += c.getRuntime();
            area += c.getArea();
        }
        JSONArray ret = new JSONArray();
        ret.put(runtime);
        ret.put(area);
        ret.put(cleanups.size());
        ret.put(cleans);
        return ret;
    }

    private Object getCleanup(JSONArray id){
        if (id == null) return null;
        VacuumCleanup c = cleanups.get(id.optLong(0, -1));
        if (c == null) return null;
        JSONArray ret = new JSONArray();
        ret.put(c.construct());
        return ret;
    }

    private Object getSoundVolume(){
        JSONArray ret = new JSONArray();
        ret.put(soundVolume);
        return ret;
    }

    private Object setSoundVolume(JSONArray vol){
        if (vol == null) return null;
        int volVal = vol.optInt(0, -1);
        if (volVal > 100 | volVal < 0) return null;
        this.soundVolume = volVal;
        return ok();
    }

    private Object testSoundVolume(){
        return ok();
    }

    private Object manualControlStart() {
        state.setState(VacuumStatus.State.REMOTE_CONTROL);
        return ok();
    }

    private Object manualControlStop() {
        state.setState(VacuumStatus.State.IDLE);
        return ok();
    }

    private Object manualControlMove(JSONArray mov) {
        if (mov == null) return null;
        JSONObject ob = mov.optJSONObject(0);
        if (ob == null) return null;
        return ok();
    }

    private Object installSoundpack(JSONObject install) {
        if (install == null) return null;
        soundSetupState.setSid(install.optInt("sid", -1));
        soundSetupState.setError(VacuumSounpackInstallState.Error.NONE);
        soundSetupState.setState(VacuumSounpackInstallState.State.UNKNOWN);
        soundSetupState.setProgress(100);
        JSONArray ret = new JSONArray();
        ret.put(soundSetupState.construct(false));
        return ret;
    }

    private Object soundpackInstallStatus() {
        JSONArray ret = new JSONArray();
        ret.put(soundSetupState.construct(true));
        return ret;
    }

    private Object getCarpetModeState(){
        JSONArray ret = new JSONArray();
        ret.put(carpedMode);
        return ret;
    }

    private Object setCarpetMode(JSONArray mode) {
        if (mode == null) return null;
        JSONObject cMode = mode.optJSONObject(0);
        if (cMode == null) return null;
        this.carpedMode = cMode;
        return ok();
    }

    private Object getSerialnumber() {
        JSONArray ret = new JSONArray();
        ret.put(serialNumber);
        return ret;
    }

    private JSONArray ok(){
        JSONArray ret = new JSONArray();
        ret.put("ok");
        return ret;
    }

}
