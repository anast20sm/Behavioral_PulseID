package com.pulseid.behavioralpulseid;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.CorrelationAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Evaluation;
import weka.classifiers.misc.IsolationForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ArffLoader;

class MlDataCollector {
    private final Context context;
    private static String ARFFPATH;
    static String TESTPATH;
    private static String PROFILEPATH;
    private static List attAppNames = new ArrayList();
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;


    MlDataCollector(Context context) {
        this.context = context;
        pref = context.getSharedPreferences("pulseidpreferences", 0); // 0 - for private mode
        editor = pref.edit();
        editor.putStringSet("packages", constructPackages()).commit();

        ARFFPATH = context.getFilesDir().getPath().concat("/dataset.arff");
        TESTPATH = context.getFilesDir().getPath().concat("/testset.arff");
        if (pref.getBoolean("eval-owner",true)){
            PROFILEPATH = context.getFilesDir().getPath().concat("/ownerdataset.arff");
        }else{
            PROFILEPATH = context.getFilesDir().getPath().concat("/impostordataset.arff");
        }
    }

    private Set<String> constructPackages() {
        Set<String> pcks = new HashSet<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, 0, System.currentTimeMillis());
            for (UsageStats model : stats) {
                pcks.add(model.getPackageName());
            }
        }
        return pcks;
    }

    private Instances createArffStruct() {
        ArrayList<Attribute> atts;
        // 1. set up attributes
        atts = new ArrayList<>();
        // - numeric
        atts.add(new Attribute("brightness"));
        atts.add(new Attribute("orientation"));
        atts.add(new Attribute("light"));
        atts.add(new Attribute("memmory usage"));
        atts.add(new Attribute("network statistics Tx"));
        atts.add(new Attribute("network statistics Rx"));
        atts.add(new Attribute("bluetooth statistics"));
        atts.add(new Attribute("unlock time"));
        atts.add(new Attribute("unlocks"));
        atts.add(new Attribute("horario"));
        atts.add(new Attribute("date"));
        atts.add(new Attribute("dia"));

        if (pref.getStringSet("packages",new HashSet<String>()).isEmpty())
            editor.putStringSet("packages", constructPackages()).commit();

        atts.add(new Attribute("fromApp"));
        atts.add(new Attribute("toApp"));
        atts.add(new Attribute("lastMinuteApps"));
        atts.add(new Attribute("mostUsedLastDay"));
        atts.add(new Attribute("secondMostUsedLastDay"));

        List classAttr = new ArrayList();
        classAttr.add(Integer.toString(1));
        classAttr.add(Integer.toString(-1));
        atts.add(new Attribute("itIs", classAttr));

        // 2. create Instances object
        Instances data = new Instances("senseID behaviour", atts, 0);
        data.setClassIndex(data.numAttributes()-1);

        return data;
    }

    private double[] collectData(float brighness, int orientation, float lightSensor, double[] memmory, long[] networkStats, long bluetoothStats,
                                long lockTime, long unlocks, String[] pausedToResumed, int appsLastMinute,
                                String[] mostUsedLastDay) {

        // 3. fill with data
        // first instance
        double[] vals = new double[18];
        // - numeric
        vals[0] = brighness;
        vals[1] = orientation;
        vals[2] = lightSensor;
        vals[3] = memmory[0];
        vals[4] = networkStats[0];
        vals[5] = networkStats[1];
        vals[6] = bluetoothStats;
        vals[7] = lockTime;
        vals[8] = unlocks;
        // - nominal
        Calendar rightNow = Calendar.getInstance();
        int currentHour = rightNow.get(Calendar.HOUR_OF_DAY);

        List attNomHour = new ArrayList(6);
        for (int i = 0; i < 6; i++)
            attNomHour.add("temporal zone " + (i + 1));
        if (currentHour >= 0 && currentHour < 4)
            vals[9] = attNomHour.indexOf("temporal zone 1");
        else if (currentHour >= 4 && currentHour < 8)
            vals[9] = attNomHour.indexOf("temporal zone 2");
        else if (currentHour >= 8 && currentHour < 12)
            vals[9] = attNomHour.indexOf("temporal zone 3");
        else if (currentHour >= 12 && currentHour < 16)
            vals[9] = attNomHour.indexOf("temporal zone 4");
        else if (currentHour >= 16 && currentHour < 20)
            vals[9] = attNomHour.indexOf("temporal zone 5");
        else if (currentHour >= 20 && currentHour < 24)
            vals[9] = attNomHour.indexOf("temporal zone 6");
        // - date (only the day of month)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vals[10] = rightNow.get(Calendar.DAY_OF_MONTH);
        }
        // - day of week
        List attNomDay = new ArrayList(7);
        attNomDay.add("Monday");
        attNomDay.add("Tuesday");
        attNomDay.add("Wednesday");
        attNomDay.add("Thursday");
        attNomDay.add("Friday");
        attNomDay.add("Saturday");
        attNomDay.add("Sunday");
        vals[11] = attNomDay.indexOf(new SimpleDateFormat("EEEE", Locale.ENGLISH).format(rightNow.getTime().getTime()));

        if (pausedToResumed[0].equals("")) {
            pausedToResumed[0] = BackgroundService.lastAppInForeground;
        }
        if (pausedToResumed[1].equals("")) {
            pausedToResumed[1] = BackgroundService.lastAppInForeground;
        }
        attAppNames.addAll(pref.getStringSet("packages", new HashSet<String>()));
        if (pref.getStringSet("packages",new HashSet<String>()).isEmpty() || attAppNames.indexOf(pausedToResumed[0]) == (-1) || attAppNames.indexOf(pausedToResumed[1]) == (-1) || attAppNames.indexOf(mostUsedLastDay[0]) == (-1) || attAppNames.indexOf(mostUsedLastDay[1]) == (-1))
            editor.putStringSet("packages",constructPackages()).commit();

        /*System.out.println("pausedToResumed[0] = "+pausedToResumed[0] + " {" +attAppNames.indexOf(pausedToResumed[0])+"}\n" +
                "pausedToResumed[1] = "+ pausedToResumed[1] + " {" + attAppNames.indexOf(pausedToResumed[1]) +"}\n" +
                "mostUsedLastDay[0] = "+mostUsedLastDay[0]+"{"+attAppNames.indexOf(mostUsedLastDay[0])+"}\n" +
                "mostUsedLastDay[1] = "+mostUsedLastDay[1]+"{"+attAppNames.indexOf(mostUsedLastDay[1])+"}\n");*/
        vals[12] = attAppNames.indexOf(pausedToResumed[0]);
        vals[13] = attAppNames.indexOf(pausedToResumed[1]);
        vals[14] = appsLastMinute;
        vals[15] = attAppNames.indexOf(mostUsedLastDay[0]);
        vals[16] = attAppNames.indexOf(mostUsedLastDay[1]);

        vals[17] = 0;// this is nominal value 1 (class value)

        //Print all
        String bluetoothPrint = String.valueOf(bluetoothStats);
        if (bluetoothStats>100)
             bluetoothPrint = String.valueOf(bluetoothStats-100);

        editor.putString("params_text","Current collected parameters (last 1m):\n\n" +
                " - Brighness: "+brighness+"\n" +
                " - Light sensor: "+lightSensor+"\n" +
                " - Memory: "+memmory[0]+" kB"+"("+(int)memmory[1]+"%)\n" +
                " - NetworkStats: "+networkStats[0]+" MB(Tx), "+networkStats[1]+" MB(Rx)\n" +
                " - BluetoothStats: "+bluetoothPrint+" (bonded | connected)\n" +
                " - LockedTime: "+lockTime+" s\n" +
                " - Unlocks: "+unlocks+"\n" +
                " - First app: " + pausedToResumed[0] +"\n"+
                " - Last app: " + pausedToResumed[1] +"\n" +
                " - Number of apps: "+appsLastMinute+"\n" +
                " - Most used last day: "+mostUsedLastDay[0]+" & "+mostUsedLastDay[1]).commit();
        //MainActivity.paramsView.setText(pref.getString("params_text",null));
        return vals;
    }

    private void writeArff(Instances data, String path) throws IOException {
        //WRITE THE COLLECTED INFORMATION INTO THE ARFF FILE
        FileWriter file = new FileWriter(path);
        BufferedWriter writer = new BufferedWriter(file);
        String s = data.toString();
        writer.write(s);
        writer.flush();
        writer.close();
    }

    private Instances readArff(String path) throws IOException {
        //READ FROM THE ARFF FILE THAT CONTAINS THE ARFF INFORMATION
        BufferedReader reader = new BufferedReader(new FileReader(path));
        ArffLoader.ArffReader arff = new ArffLoader.ArffReader(reader);
        Instances instances = arff.getData();
        instances.setClassIndex(instances.numAttributes()-1);
        reader.close();
        return instances;
    }

    void train(float brighness, int orientation, float lightSensor, double[] memmory, long[] networkStats, long bluetoothStats,
               long lockTime, long unlocks, String[] pausedToResumed, int appsLastMinute, String[] mostUsedLastDay) throws Exception {
        Instances data;
        if (new File(MlDataCollector.ARFFPATH).exists()) {
            data = readArff(ARFFPATH);
        } else {
            data = createArffStruct();
        }
        double[] vals = collectData(brighness, orientation, lightSensor, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastMinute, mostUsedLastDay);
        data.add(new DenseInstance(1.0, vals));
        writeArff(data, ARFFPATH);
        editor.putString("debug_text",new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(System.currentTimeMillis()))+" New train instance added."+"\n"+pref.getString("debug_text",null)).commit();
        //MainActivity.debugView.setText(pref.getString("debug_text",null));
    }

    double[] test(float brighness, int orientation, float lightSensor, double[] memmory, long[] networkStats,
                  long bluetoothStats, long lockTime, long unlocks,
                  String[] pausedToResumed, int appsLastMinute, String[] mostUsedLastDay) throws Exception {
        // Loading arff dataset
        Instances trainingDataSet = readArff(ARFFPATH);
        trainingDataSet.setClassIndex(trainingDataSet.numAttributes() - 1);
        //Build IsolationForest classifier
        IsolationForest forest = new IsolationForest();
        forest.buildClassifier(trainingDataSet);

        // Evaluate classifier
        Instances test;
        double[] testVals = collectData(brighness, orientation, lightSensor, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastMinute, mostUsedLastDay);
        if (!new File(TESTPATH).exists()) { //If test instances do not exist, create the file and write last instance
            test = createArffStruct();
            test.add(new DenseInstance(1.0, testVals));
            test.setClassIndex(test.numAttributes() - 1);
            writeArff(test, PROFILEPATH);
        } else {    //If test file exists, reads it, removes older and adds new one
            test = readArff(TESTPATH);
            test.add(new DenseInstance(1.0, testVals));
            test.setClassIndex(test.numAttributes() - 1);
            writeArff(test, PROFILEPATH);
            if (test.size() >= 20) {
                test.delete(0);
                for (int i = 0; i < (test.size() - 1); i++) {
                    test.set(i, test.instance(i + 1));
                }
            }
        }
        writeArff(test, TESTPATH);

        Evaluation eval = new Evaluation(trainingDataSet);
        eval.evaluateModel(forest, test);
        System.out.println(
                eval.toSummaryString("\nResults\n======\n", false)
                        + "\n" + eval.toMatrixString()
        );
        editor.putString("debug_text",new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(System.currentTimeMillis()))+" Evaluation successful."+"\n"+pref.getString("debug_text",null)).commit();
        //MainActivity.debugView.setText(pref.getString("debug_text",null));

        //evaluate(trainingDataSet);
        return new double[]{eval.pctCorrect(),eval.meanAbsoluteError()};
    }

    public void evaluate(Instances data) {

        try {
        AttributeSelection attsel = new AttributeSelection();  // package weka.attributeSelection!
        CorrelationAttributeEval eval = new CorrelationAttributeEval();
        Ranker ranker = new Ranker();
        attsel.setEvaluator(eval);
        attsel.setSearch(ranker);
        attsel.SelectAttributes(data);

        // obtain the attribute indices that were selected
        int[] indices = attsel.selectedAttributes();
        System.out.println(Utils.arrayToString(indices));
        } catch (Exception e) {
            e.printStackTrace();
        }



        //return attEval;
    }
}
