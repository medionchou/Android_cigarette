package com.medion.project_cigarette;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {

    private Client client;
    private RecipeAsyncTask recipeAsyncTask;

    private MarqueeTextView runningTextView;
    private TableLayout tableLayout;
    private LinearLayout linearLayout;
    private Timer timer;
    ProgressDialog restartDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        runningTextView = (MarqueeTextView) findViewById(R.id.running_text_view);
        tableLayout = (TableLayout) findViewById(R.id.recipe_table_layout);
        linearLayout = (LinearLayout) findViewById(R.id.lineary_layout);
        tableLayout.setStretchAllColumns(true);
        restartDialog = new ProgressDialog(this);

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (client == null) {
            client = new Client(this);
            client.start();
        }
        recipeAsyncTask = new RecipeAsyncTask();
        recipeAsyncTask.execute((Void) null);

    }

    @Override
    protected void onStop() {
        super.onStop();
        client.setTerminated();
        if (!recipeAsyncTask.isCancelled())
            recipeAsyncTask.cancel(true);
        defRefObject();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_settings:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final LinearLayout ip_portLayout = (LinearLayout)getLayoutInflater().inflate(R.layout.ip_port_layout, null);
                builder.setTitle("設定IP及PORT");
                builder.setView(ip_portLayout);

                builder.setPositiveButton("確認", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences settings = getSharedPreferences("IPFILE", 0);
                        SharedPreferences.Editor editor = settings.edit();
                        EditText ipView = (EditText) ip_portLayout.findViewById(R.id.ip);
                        EditText portView = (EditText) ip_portLayout.findViewById(R.id.port);
                        String ipTest = ipView.getText().toString();
                        String portTest = portView.getText().toString();
                        String ip = "127.0.0.1";
                        Pattern pattern = Pattern.compile("[0-9]{1,3}+.[0-9]{1,3}+.[0-9]{1,3}+.[0-9]{1,3}+");
                        Matcher matcher = pattern.matcher(ipTest);
                        int port = 0;
                        boolean checker = false;

                        if (!portTest.equals("")) {
                            if (Integer.valueOf(portTest) <= 65536) {
                                port = Integer.valueOf(portTest);
                                checker = true;
                            }
                        }

                        checker = matcher.matches();

                        if (matcher.matches()) {
                            String[] strip = ipTest.split("\\.");
                            boolean isMatch = true;

                            for (String tmp : strip) {
                                if (Integer.valueOf(tmp) > 255)
                                    isMatch = false;
                            }

                            if (isMatch)
                                ip = ipTest;

                            checker |= isMatch;
                        }

                        if (checker) {
                            editor.putString("IP", ip);
                            editor.putInt("PORT", port);
                            editor.apply();
                        } else {
                            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                            alert.setTitle("警告");
                            alert.setMessage("IP 或 PORT 設定錯誤");
                            alert.show();
                        }
                    }
                });
                builder.show();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void defRefObject() {
        client = null;
    }

    public void restartActivity(int type) {
        if (type == States.SERVER_RESTART) {
            restartDialog.setTitle("警告");
            restartDialog.setMessage("伺服器連線失敗\n將於5秒鐘重啟");
            restartDialog.show();
            restartDialog.setCancelable(false);
        }

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (restartDialog.isShowing())
                    restartDialog.dismiss();
                Intent intent = new Intent();
                finish();
                startActivity(intent);

            }
        }, 10000);

    }

    public void setBackground(int color) {
        linearLayout.setBackgroundColor(color);
    }

    private class RecipeAsyncTask extends AsyncTask<Void, String, Void> {
        ProgressDialog progressDialog;
        List<Recipe> recipeList;
        String reply = "";
        String msg = "";
        String oldMsg = "";
        int count = 0;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            recipeList = new ArrayList<>();
            progressDialog.setTitle("請稍後");
            progressDialog.setMessage("取得資料中");
            progressDialog.show();
            progressDialog.setCancelable(false);
            progressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    String msg = ((TextView)progressDialog.findViewById(android.R.id.message)).getText().toString();

                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        if (count == 40) {
                            count = 0;
                            dialog.dismiss();
                        } else {
                            count++;
                        }
                    }


                    return false;
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                while (client.getClientState() != States.CONNECT_OK) {
                    Thread.sleep(1000);
                }

                client.setCmd(Command.RECIPE_NOW);
                Thread.sleep(2000);

                while (reply.length() == 0) {
                    reply = client.getQueryReply();
                    Thread.sleep(1000);
                }

                parseRecipeMsg(reply, false);

                client.resetQueryReply();

                publishProgress("");

                while (!isCancelled()) {
                    reply = client.getUpdateMsg();
                    msg = client.getMsg();

                    if (reply.length() > 0) {
                        String[] updateInfo = reply.split("<END>");

                        for (String tmp : updateInfo) {
                            if (tmp.contains("UPDATE_RECIPE_NOW")) {
                                parseRecipeMsg(tmp, true);
                                publishProgress("");
                            }
                        }
                        client.resetUpdateMsg();
                    }

                    if (msg.length() > 0 && !oldMsg.equals(msg)) {
                        publishProgress(msg, "true");
                    } else if (msg.length() > 0) {
                        Calendar cal = Calendar.getInstance();
                        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                        String date = dateFormat.format(cal.getTime());
                        publishProgress(msg + date, "false");
                    }

                    oldMsg = msg;
                    Thread.sleep(1000);
                }

            } catch (InterruptedException e) {
                Log.e("MyLog", e.toString());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if (progressDialog.isShowing())
                progressDialog.dismiss();

            if (values[0].length() > 0) {
                if (values[1].equals("true"))
                    runningTextView.setText(values[0]);
                else if (values[1].equals("false")) {
                    runningTextView.setNewText(values[0]);
                }
            } else {
                updateGui();
            }
        }

        private void parseRecipeMsg(String recipeMsg, boolean update) {

            String[] data = recipeMsg.split("\\t|<N>|<END>");

            if (update) {
                Recipe recipe = new Recipe(data[1], data[2], data[3]);

                for (int i = 0; i < recipeList.size(); i++) {
                    Recipe tmp = recipeList.get(i);
                    if (recipe.isBucketMatch(tmp)) {
                        recipeList.set(i, recipe);
                    }
                }

            } else {
                for (int i = 0; i < data.length; i = i + 4) {
                    Recipe recipe = new Recipe(data[i + 1], data[i + 2], data[i + 3]);
                    recipeList.add(recipe);
                }
            }

        }

        private void updateGui() {
            tableLayout.removeAllViews();

            for (int i = 0; i < recipeList.size(); i++) {
                Recipe recipe = recipeList.get(i);
                inflateTextView(recipe, i);
            }
        }

        private void inflateTextView(Recipe recipe, int indexToInflate) {

            TableLayout.LayoutParams tableRowParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1);
            TableRow.LayoutParams textViewParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
            TableRow tableRow = new TableRow(MainActivity.this);
            TextView bucketTextView = new TextView(MainActivity.this);
            TextView recipeIdTextView = new TextView(MainActivity.this);
            TextView recipeNameTextView = new TextView(MainActivity.this);

            tableRow.setLayoutParams(tableRowParams);
            bucketTextView.setLayoutParams(textViewParams);
            recipeNameTextView.setLayoutParams(textViewParams);
            recipeIdTextView.setLayoutParams(textViewParams);

            bucketTextView.setText(recipe.getBucketNum());
            recipeNameTextView.setText(recipe.getRecipeName());
            recipeIdTextView.setText(recipe.getRecipeId());

            bucketTextView.setTextSize(Config.TEXT_SIZE);
            recipeNameTextView.setTextSize(Config.TEXT_SIZE);
            recipeIdTextView.setTextSize(Config.TEXT_SIZE);

            recipeIdTextView.setMaxEms(5);

            if (indexToInflate == 0) {
                TableRow titleRow = new TableRow(MainActivity.this);
                TextView bucketTitle= new TextView(MainActivity.this);
                TextView recipeIdTitle = new TextView(MainActivity.this);
                TextView recipeNameTitle = new TextView(MainActivity.this);

                titleRow.setLayoutParams(tableRowParams);
                bucketTitle.setLayoutParams(textViewParams);
                recipeIdTitle.setLayoutParams(textViewParams);
                recipeNameTitle.setLayoutParams(textViewParams);

                bucketTitle.setText("桶號");
                recipeNameTitle.setText("配方名稱");
                recipeIdTitle.setText("配方編號");

                bucketTitle.setTextSize(Config.TEXT_TITLE_SIZE);
                recipeNameTitle.setTextSize(Config.TEXT_TITLE_SIZE);
                recipeIdTitle.setTextSize(Config.TEXT_TITLE_SIZE);

                bucketTitle.setTextColor(Color.BLACK);
                recipeNameTitle.setTextColor(Color.BLACK);
                recipeIdTitle.setTextColor(Color.BLACK);

                bucketTitle.setTypeface(null, Typeface.BOLD);
                recipeNameTitle.setTypeface(null, Typeface.BOLD);
                recipeIdTitle.setTypeface(null, Typeface.BOLD);

                titleRow.addView(bucketTitle);
                titleRow.addView(recipeNameTitle);
                titleRow.addView(recipeIdTitle);

                tableLayout.addView(titleRow);
            }

            tableRow.addView(bucketTextView);
            tableRow.addView(recipeIdTextView);
            tableRow.addView(recipeNameTextView);

            tableLayout.addView(tableRow);
        }

    }
}
