package org.disrupted.rumble.userinterface.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.disrupted.rumble.R;
import org.disrupted.rumble.contact.Contact;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Marlinski
 */
public class PopupCompose extends Activity {

    private static final String TAG = "PopupCompose";
    public static final int REQUEST_IMAGE_CAPTURE = 42;

    private LinearLayout dismiss;
    private EditText    compose;
    private ImageButton takePicture;
    private ImageButton choosePicture;
    private ImageButton send;

    private Bitmap imageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.compose_popup);

        dismiss = (LinearLayout)(findViewById(R.id.popup_dismiss));
        compose = (EditText)(findViewById(R.id.popup_user_status));
        takePicture = (ImageButton)(findViewById(R.id.popup_take_picture));
        choosePicture = (ImageButton)(findViewById(R.id.popup_choose_image));
        send = (ImageButton)(findViewById(R.id.popup_button_send));

        dismiss.setOnClickListener(onDiscardClick);
        takePicture.setOnClickListener(onTakePictureClick);
        choosePicture.setOnClickListener(onAttachePictureClick);
        send.setOnClickListener(onClickSend);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    View.OnClickListener onDiscardClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(compose.getWindowToken(), 0);
            finish();
        }
    };

    View.OnClickListener onTakePictureClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final Activity activity = PopupCompose.this;
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    };

    View.OnClickListener onAttachePictureClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
        }
    };

    View.OnClickListener onClickSend = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (compose == null)
                return;

            String message = compose.getText().toString();
            if ((message == "") && (imageBitmap == null))
                return;

            Contact localContact = DatabaseFactory.getContactDatabase(PopupCompose.this).getLocalContact();
            long now = (System.currentTimeMillis() / 1000L);
            StatusMessage statusMessage = new StatusMessage(message, localContact.getName(), now);
            statusMessage.setUserRead(true);

            try {
               String filename = saveImageOnDisk();
                statusMessage.setFileName(filename);
            }
            catch (IOException ignore) {
            }

            compose.setText("");

            //todo: replace by an Event !!
            DatabaseFactory.getStatusDatabase(PopupCompose.this).insertStatus(statusMessage, null);

            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(compose.getWindowToken(), 0);
            finish();
        }
    };

    //todo review this code, probably a bit dirty
    public String saveImageOnDisk() throws IOException {
        String filename;
        if(imageBitmap == null)
            throw new IOException();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        filename = "JPEG_" + timeStamp + ".jpeg";
        // todo getByteCount returns the number of byte on-memory, not on-disk
        File storageDir = FileUtil.getWritableAlbumStorageDir(imageBitmap.getByteCount());
        String path = storageDir.toString() + File.separator + filename;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (Exception e) {
            imageBitmap = null;
            return null;
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException ignore) { }
        }
        imageBitmap = null;

        return filename;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
        }
    }
}
