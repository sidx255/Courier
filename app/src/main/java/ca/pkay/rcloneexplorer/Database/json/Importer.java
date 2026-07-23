package ca.pkay.rcloneexplorer.Database.json;


import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

import ca.pkay.rcloneexplorer.Database.DatabaseHandler;
import ca.pkay.rcloneexplorer.Items.Filter;
import ca.pkay.rcloneexplorer.Items.Task;
import ca.pkay.rcloneexplorer.Items.Trigger;
import ca.pkay.rcloneexplorer.Services.TriggerService;

public class Importer {


    public static void importJson(String json, Context context) throws JSONException {
        ArrayList<Trigger> triggers = createTriggerlist(json);
        ArrayList<Filter> filters = createFilterList(json);
        ArrayList<Task> tasks = createTasklist(json);

        DatabaseHandler dbHandler = new DatabaseHandler(context);
        TriggerService triggerService = new TriggerService(context);

        // Cancel the exact alarms of every trigger that is about to be wiped. Otherwise their
        // alarms stay scheduled with no matching row (orphans), and because trigger IDs are
        // reusable SQLite ROWIDs an imported trigger can inherit an old ID and be fired at the
        // stale alarm's time, launching the wrong task.
        for (Trigger existing : dbHandler.getAllTrigger()) {
            triggerService.cancelTrigger(existing.getId());
        }

        dbHandler.deleteEveryting();
        for(Trigger trigger : triggers){
            dbHandler.createTrigger(trigger, true);
        }
        for(Filter filter : filters){
            dbHandler.createFilter(filter, true);
        }
        for(Task task : tasks){
            dbHandler.createTask(task, true);
        }

        // Arm the freshly imported triggers immediately so scheduled backups fire without
        // waiting for the next app launch, boot, or time change.
        triggerService.queueTrigger();
    }

    public static void validateJson(String json) throws JSONException {
        createTriggerlist(json);
        createFilterList(json);
        createTasklist(json);
    }

    public static ArrayList<Trigger> createTriggerlist(String content) throws JSONException {
        ArrayList<Trigger> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.getJSONArray("trigger");
        for (int i = 0; i < array.length(); i++) {
            JSONObject triggerObject = array.getJSONObject(i);
            result.add(Trigger.Companion.fromString(triggerObject.toString()));
        }
        return result;
    }

    public static ArrayList<Task> createTasklist(String content) throws JSONException {
        ArrayList<Task> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.getJSONArray("tasks");
        for (int i = 0; i < array.length(); i++) {
            JSONObject taskObject = array.getJSONObject(i);
            result.add(Task.Companion.fromString(taskObject.toString()));
        }
        return result;
    }
    public static ArrayList<Filter> createFilterList(String content) throws JSONException {
        ArrayList<Filter> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.getJSONArray("filters");
        for (int i = 0; i < array.length(); i++) {
            JSONObject filterObject = array.getJSONObject(i);
            result.add(Filter.Companion.fromString(filterObject.toString()));
        }
        return result;
    }
}
