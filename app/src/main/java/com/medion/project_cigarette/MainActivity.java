package com.medion.project_cigarette;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.Barcode39;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {

    private Client client;
    private RecipeAsyncTask recipeAsyncTask;

    private TextView runningTextView;
    private TableLayout tableLayout;
    private Timer timer;
    ProgressDialog restartDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        runningTextView = (TextView) findViewById(R.id.running_text_view);
        tableLayout = (TableLayout) findViewById(R.id.recipe_table_layout);
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

    private class RecipeAsyncTask extends AsyncTask<Void, String, Void> {
        ProgressDialog progressDialog;
        List<Recipe> recipeList;
        String reply = "";
        String msg = "";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            recipeList = new ArrayList<>();
            progressDialog.setTitle("請稍後");
            progressDialog.setMessage("取得資料中");
            progressDialog.show();
            progressDialog.setCancelable(false);
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

                        parseRecipeMsg(reply, true);
                        publishProgress("");
                        client.resetUpdateMsg();
                    }

                    if (msg.length() > 0) {

                        publishProgress(msg);
                        client.resetMsg();
                    }

                    Thread.sleep(10000);
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
                runningTextView.setText(values[0]);
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
            TableRow.LayoutParams textViewParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
            TableRow tableRow = new TableRow(MainActivity.this);
            TextView bucketTextView = new TextView(MainActivity.this);
            TextView recipeIdTextView = new TextView(MainActivity.this);
            TextView recipeNameTextView = new TextView(MainActivity.this);

            tableRow.setLayoutParams(tableRowParams);
            bucketTextView.setLayoutParams(textViewParams);
            recipeIdTextView.setLayoutParams(textViewParams);
            recipeNameTextView.setLayoutParams(textViewParams);

            bucketTextView.setText(recipe.getBucketNum());
            recipeIdTextView.setText(recipe.getRecipeId());
            recipeNameTextView.setText(recipe.getRecipeName());

            bucketTextView.setTextSize(Config.TEXT_SIZE);
            recipeIdTextView.setTextSize(Config.TEXT_SIZE);
            recipeNameTextView.setTextSize(Config.TEXT_SIZE);

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
                recipeIdTitle.setText("配方編號");
                recipeNameTitle.setText("配方名稱");

                bucketTitle.setTextSize(Config.TEXT_TITLE_SIZE);
                recipeIdTitle.setTextSize(Config.TEXT_TITLE_SIZE);
                recipeNameTitle.setTextSize(Config.TEXT_TITLE_SIZE);

                bucketTitle.setTextColor(Color.BLACK);
                recipeIdTitle.setTextColor(Color.BLACK);
                recipeNameTitle.setTextColor(Color.BLACK);

                bucketTitle.setTypeface(null, Typeface.BOLD);
                recipeIdTitle.setTypeface(null, Typeface.BOLD);
                recipeNameTitle.setTypeface(null, Typeface.BOLD);

                titleRow.addView(bucketTitle);
                titleRow.addView(recipeIdTitle);
                titleRow.addView(recipeNameTitle);

                tableLayout.addView(titleRow);
            }

            tableRow.addView(bucketTextView);
            tableRow.addView(recipeIdTextView);
            tableRow.addView(recipeNameTextView);

            tableLayout.addView(tableRow);
        }

    }
}
