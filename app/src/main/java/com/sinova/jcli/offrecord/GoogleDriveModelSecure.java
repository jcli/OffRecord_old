package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Base64;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.metadata.CustomPropertyKey;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by jcli on 4/26/16.
 */
public class GoogleDriveModelSecure extends GoogleDriveModel {
    private SecretKey mKeyEncryptionKey=null;  // must never be stored, and should be cleared on timeout.
    private byte[] mSalt = null;

    private String theTestText = "x";

    public GoogleDriveModelSecure(Activity callerContext) {
        super(callerContext);
        String password  = "password";
        int iterationCount = 10000;
        int keyLength = 256;
        int saltLength = keyLength / 8; // same size as key output

        SecureRandom random = new SecureRandom();
        mSalt = new byte[saltLength];
        random.nextBytes(mSalt);
        KeySpec keySpec = new PBEKeySpec(password.toCharArray(), mSalt,
                iterationCount, keyLength);
        SecretKeyFactory keyFactory = null;
        try {
            keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] keyBytes = new byte[0];
        try {
            keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        mKeyEncryptionKey = new SecretKeySpec(keyBytes, "AES");
    }

    public void validateKeyEncryptionKey(){
        super.listAppRoot(new ListFolderByIDCallback() {
            @Override
            public void callback(FolderInfo info) {
                DriveId fileID=null;
                Metadata fileMetaData=null;
                for (int i=0; i<info.items.length; i++) {
                    if (info.items[i].getTitle().equals(mParentActivity.getString(R.string.password_validation_file))) {
                        fileID = info.items[i].getDriveId();
                        fileMetaData = info.items[i];
                        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "validation file: "+info.items[i].getTitle());
                        break;
                    }
                }
                if (fileID!=null){
                    // found validation file.  Do validation
                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "password validation file found. Validating...");
                    final DriveFile driveFile = fileID.asDriveFile();
                    // get metadata
                    Map customProperties = fileMetaData.getCustomProperties();
                    String ivString = (String) customProperties.get(new CustomPropertyKey("iv", CustomPropertyKey.PUBLIC));
                    String saltString= (String) customProperties.get(new CustomPropertyKey("salt", CustomPropertyKey.PUBLIC));
                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "iv   :" + ivString);
                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "salt :" + saltString);
                    byte[] iv = Base64.decode(ivString.getBytes(), Base64.DEFAULT);
                    byte[] salt = Base64.decode(saltString.getBytes(), Base64.DEFAULT);
                    IvParameterSpec ivParams = new IvParameterSpec(iv);
                    String password  = "password";
                    int iterationCount = 10000;
                    int keyLength = 256;
                    KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt,
                            iterationCount, keyLength);
                    SecretKeyFactory keyFactory = null;
                    try {
                        keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                    byte[] keyBytes = new byte[0];
                    try {
                        keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
                    } catch (InvalidKeySpecException e) {
                        e.printStackTrace();
                    }
                    SecretKey keyToValidate = new SecretKeySpec(keyBytes, "AES");

                    // try decrypt
                    Cipher cipher = null;
                    try {
                        cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                    }
                    try {
                        cipher.init(Cipher.DECRYPT_MODE, keyToValidate, ivParams);
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (InvalidAlgorithmParameterException e) {
                        e.printStackTrace();
                    }
                    final Cipher finalCipher = cipher;
                    readTxtFile(fileID.encodeToString(), new ReadTxtFileCallback(){
                        @Override
                        public void callback(String fileContent) {
                            byte[] plaintext = new byte[0];
                            try {
                                byte[] contentBytes = fileContent.getBytes();
                                //contentBytes[0]=12;
                                plaintext = finalCipher.doFinal(Base64.decode(contentBytes, Base64.DEFAULT));
                            } catch (IllegalBlockSizeException e) {
                                e.printStackTrace();
                            } catch (BadPaddingException e) {
                                e.printStackTrace();
                            }
                            String plainrStr=null;
                            try {
                                plainrStr = new String(plaintext , "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "decoded message: " + plainrStr);
                            // delete for now:
                            driveFile.delete(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
                                @Override
                                public void onResult(@NonNull Status status) {
                                    if (status.isSuccess()){
                                        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "password validation file deleted.");
                                    }
                                }
                            });
                        }
                    });
                }else{
                    // validation file not found.  Create it
                    // encrypt the test string
                    Cipher cipher = null;
                    try {
                        cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                    }
                    final byte[] iv = new byte[cipher.getBlockSize()];
                    SecureRandom random = new SecureRandom();
                    random.nextBytes(iv);
                    IvParameterSpec ivParams = new IvParameterSpec(iv);
                    try {
                        cipher.init(Cipher.ENCRYPT_MODE, mKeyEncryptionKey, ivParams);
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (InvalidAlgorithmParameterException e) {
                        e.printStackTrace();
                    }
                    byte[] ciphertext=null;
                    try {
                        ciphertext = cipher.doFinal(theTestText.getBytes("UTF-8"));
                    } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                    } catch (BadPaddingException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    final String encryptedText = Base64.encodeToString(ciphertext, Base64.DEFAULT);
                    createTxtFile(mParentActivity.getString(R.string.password_validation_file),
                            info.folder.getDriveId().encodeToString(),
                            new ListFolderByIDCallback() {
                                @Override
                                public void callback(FolderInfo info) {
                                    String fileID=null;
                                    for(int i=0; i< info.items.length; i++) {
                                        if (info.items[i].getTitle().equals(mParentActivity.getString(R.string.password_validation_file))) {
                                            fileID = info.items[i].getDriveId().encodeToString();
                                            break;
                                        }
                                    }
                                    Map<String, String> metaData = new HashMap<String, String>();
                                    metaData.put("salt", Base64.encodeToString(mSalt, Base64.DEFAULT));
                                    metaData.put("iv", Base64.encodeToString(iv, Base64.DEFAULT));
                                    if (fileID!=null){
                                        writeTxtFile(fileID, encryptedText, new WriteTxtFileCallback() {
                                            @Override
                                            public void callback(boolean success) {
                                                JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "password validation file created.");
                                            }
                                        }, metaData);
                                    }
                                }
                            });
                }
            }
        });
    }
}