package com.profecialinks.ocr;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    FirebaseAuth mAuth;
    Handler handler = new Handler();
    ImageView imageView;
    TextView resultText;
    Button captureButton;
    Bitmap imageBitmap;
    FirebaseFunctions mFunctions = FirebaseFunctions.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        imageView = findViewById(R.id.image);
        resultText = findViewById(R.id.outputText);
        captureButton = findViewById(R.id.captureImage);
        resultText.setMovementMethod(new ScrollingMovementMethod());
        if (mAuth.getCurrentUser() == null)
            startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder().setAllowNewEmailAccounts(true).build(), 2);
        captureButton.setOnClickListener(v -> {
            resultText.setText("");
            if (mAuth.getCurrentUser() == null)
                startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder().setAllowNewEmailAccounts(true).build(), 2);
            else
                dispatchTakePictureIntent();
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(takePictureIntent, 1);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            imageView.setImageBitmap(imageBitmap);
            imageBitmap = scaleBitmapDown(imageBitmap);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            String base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            JsonObject request = new JsonObject();
            JsonObject image = new JsonObject();
            image.add("content", new JsonPrimitive(base64encoded));
            request.add("image", image);
            JsonObject feature = new JsonObject();
            feature.add("type", new JsonPrimitive("TEXT_DETECTION"));
            JsonArray features = new JsonArray();
            features.add(feature);
            request.add("features", features);
            JsonObject imageContext = new JsonObject();
            JsonArray languageHints = new JsonArray();
            languageHints.add("en");
            languageHints.add("ar");
            imageContext.add("languageHints", languageHints);
            request.add("imageContext", imageContext);
            annotateImage(request.toString())
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Toast toast = Toast.makeText(MainActivity.this, "Failed to annotate image !!!", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        } else {
                            JsonObject annotation = Objects.requireNonNull(task.getResult()).getAsJsonArray().get(0).getAsJsonObject().get("fullTextAnnotation").getAsJsonObject();
                            System.out.format("%nComplete annotation:%n");
                            System.out.format("%s%n", annotation.get("text").getAsString());
                            for (JsonElement page : annotation.get("pages").getAsJsonArray()) {
                                StringBuilder pageText = new StringBuilder();
                                for (JsonElement block : page.getAsJsonObject().get("blocks").getAsJsonArray()) {
                                    StringBuilder blockText = new StringBuilder();
                                    for (JsonElement para : block.getAsJsonObject().get("paragraphs").getAsJsonArray()) {
                                        StringBuilder paraText = new StringBuilder();
                                        for (JsonElement word : para.getAsJsonObject().get("words").getAsJsonArray()) {
                                            StringBuilder wordText = new StringBuilder();
                                            for (JsonElement symbol : word.getAsJsonObject().get("symbols").getAsJsonArray()) {
                                                wordText.append(symbol.getAsJsonObject().get("text").getAsString());
                                                System.out.format("Symbol text: %s (confidence: %f)%n", symbol.getAsJsonObject().get("text").getAsString(), symbol.getAsJsonObject().get("confidence").getAsFloat());
                                            }
                                            System.out.format("Word text: %s (confidence: %f)%n%n", wordText.toString(), word.getAsJsonObject().get("confidence").getAsFloat());
                                            System.out.format("Word bounding box: %s%n", word.getAsJsonObject().get("boundingBox"));
                                            paraText.append(wordText.toString()).append(" ");
                                        }
                                        System.out.format("%nParagraph:%n%s%n", paraText);
                                        System.out.format("Paragraph bounding box: %s%n", para.getAsJsonObject().get("boundingBox"));
                                        System.out.format("Paragraph Confidence: %f%n", para.getAsJsonObject().get("confidence").getAsFloat());
                                        blockText.append(paraText);
                                    }
                                    pageText.append(blockText);
                                }
                                resultText.setText(pageText);
                            }
                            Toast toast = Toast.makeText(MainActivity.this, "Success !!!", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        }
                    });
        } else if (requestCode == 2) {
            try {
                Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).reload();
                handler.postDelayed(() -> {
                    if (Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).isEmailVerified()) {
                        Toast success = Toast.makeText(this, "Welcome back " + FirebaseAuth.getInstance().getCurrentUser().getDisplayName(), Toast.LENGTH_LONG);
                        success.setGravity(Gravity.CENTER, 0, 0);
                        success.show();
                    } else {
                        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).sendEmailVerification().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast toast = Toast.makeText(this, "Please verify your email first", Toast.LENGTH_LONG);
                                toast.setGravity(Gravity.CENTER, 0, 0);
                                toast.show();
                                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.gm");
                                if (launchIntent != null) {
                                    startActivity(launchIntent);
                                    finish();
                                }
                            }
                        });
                    }
                }, 3000);
            } catch (Exception e) {
                Toast failed = Toast.makeText(this, "Register/Login Failed !!!", Toast.LENGTH_LONG);
                failed.setGravity(Gravity.CENTER, 0, 0);
                failed.show();
                handler.postDelayed(this::finish, 2000);
            }
        }
    }

    private Bitmap scaleBitmapDown(Bitmap bitmap) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = 640;
        int resizedHeight = 640;
        if (originalHeight > originalWidth) {
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    public Task<JsonElement> annotateImage(String requestJson) {
        return mFunctions
                .getHttpsCallable("annotateImage")
                .call(requestJson)
                .continueWith(task -> JsonParser.parseString(new Gson().toJson(Objects.requireNonNull(task.getResult()).getData())));
    }
}
