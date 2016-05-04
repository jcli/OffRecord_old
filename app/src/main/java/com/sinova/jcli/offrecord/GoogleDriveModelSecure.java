package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Base64;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.android.gms.drive.metadata.internal.CustomProperty;

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
 * Asset name is also encrypted
 * metadata contain the following
 *  - IV for the content
 *  - encrypted encryption key
 *  - IV for the encryption key
 *  - password salt (should be same for all asset)
 *
 */
public class GoogleDriveModelSecure extends GoogleDriveModel {

    private final int ITERATIONS = 10000;
    private final int KEYLENGTH = 256;
    private final String ASSET_NAME_PREFIX = "secure ";
    private SecretKey mKeyEncryptionKey=null;  // must never be stored, and should be cleared on timeout.
    private byte[] mSalt = null;               // should be the same for every asset
    private SecureRandom secureRandom;
    private String theTestText = "This is a test string...";

    public class FolderInfoSecure extends FolderInfo{
        public String itemNamesPlainText[];
    }

    private enum SecureProperties {
        ENCRYPTION_KEY("encryption_key"),
        ENCRYPTION_KEY_IV("encryption_key_iv"),
        ASSET_NAME("asset_name"),
        ASSET_NAME_IV("asset_name_iv"),
        CIPHER_TEXT("cipher_text"),
        CIPHER_TEXT_IV("cipher_text_iv"),
        SALT("salt");

        private String value;
        private SecureProperties(String value){this.value=value;}
        public String getValue(){return this.value;}
        @Override
        public String toString(){return this.value;}
    }

    public GoogleDriveModelSecure(Activity callerContext) {
        super(callerContext);
        secureRandom = new SecureRandom();
        // shouldn't be in the constructor
        generateSalt();
        convertPassToKey("password");  // hard code password for now
    }

    public void validateKeyEncryptionKey(){
        super.listFolderByID(mAppRootFolder.getDriveId().encodeToString(), new ListFolderByIDCallback() {
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
                    byte[] iv = Base64.decode(ivString.getBytes(), Base64.URL_SAFE);
                    byte[] salt = Base64.decode(saltString.getBytes(), Base64.URL_SAFE);
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
                                plaintext = finalCipher.doFinal(Base64.decode(contentBytes, Base64.URL_SAFE));
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
                    final String encryptedText = Base64.encodeToString(ciphertext, Base64.URL_SAFE);

                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "encrypted text: "+encryptedText +" size "+encryptedText.length());
                    createTxtFileInFolder(mParentActivity.getString(R.string.password_validation_file),
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
                                    String saltStr=Base64.encodeToString(mSalt, Base64.URL_SAFE);
                                    metaData.put("salt", saltStr);
                                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "salt text: "+saltStr+" size "+saltStr.length());
                                    String ivString = Base64.encodeToString(iv, Base64.URL_SAFE);
                                    metaData.put("iv", ivString);
                                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "iv text: "+ivString+" size "+ivString.length());
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

//    public void init(){
//        listFolderByID(mAppRootFolder.getDriveId().encodeToString(), new ListFolderByIDCallback() {
//            @Override
//            public void callback(FolderInfo info) {
//                if (info.items.length==0){
//                    // no items at all, need to create password validation file
//                    generateSalt();
//                    convertPassToKey("password");  // hard code password for now
//                    // generate a random string for asset name
//                    byte[] randomName = new byte[30];
//                    secureRandom.nextBytes(randomName);
//                    String randomNameStr = Base64.encodeToString(randomName, Base64.URL_SAFE);
//                    String assetName = encryptAssetName(randomNameStr);
//                    JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "asset Name: " + assetName);
//                    JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "asset Name length: " + String.valueOf(assetName.length()));
//                }else {
//                    // any file can be password validation file
//                }
//            }
//        });
//    }

    ////////////////////// override public methods ////////////

    public void createTxtFileInFolder(final String fileName, final String folderIdStr, final ListFolderByIDCallback callbackInstance){

    }

    @Override
    public void createFolderInFolder(final String name, final String folderIdStr, final boolean gotoFolder,
                                          final ListFolderByIDCallback callbackInstance, final Map<String, String> metaInfo){
        Map<String, String> cipherData = encryptAssetName(name);
        String encryptedName = cipherData.remove(SecureProperties.ASSET_NAME.toString());
        cipherData.put(SecureProperties.SALT.toString(), Base64.encodeToString(mSalt, Base64.URL_SAFE));
        super.createFolderInFolder(encryptedName, folderIdStr, gotoFolder, callbackInstance, cipherData);
    }
    @Override
    public void createFolderInFolder(final String name, final String folderIdStr,
                                     final boolean gotoFolder, final ListFolderByIDCallback callbackInstance){
        JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "secure create folder: " + name);
        createFolderInFolder(name, folderIdStr, gotoFolder, callbackInstance, null);
    }

    @Override
    protected void initAppRoot(ListFolderByIDCallback callbackInstance){
        final String driveRoot = Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId().encodeToString();
        String name = mParentActivity.getString(R.string.app_name);
        super.createFolderInFolder(name, driveRoot, true, callbackInstance, null);
    }

    protected boolean nameCompare(String name, Metadata item){
        Map<CustomPropertyKey, String> properties = item.getCustomProperties();
        String encryptedEncryptionKey = (String) properties.get(new CustomPropertyKey(SecureProperties.ENCRYPTION_KEY.toString(), CustomPropertyKey.PUBLIC));
        if (encryptedEncryptionKey==null) {
            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "asset name is not encrypted.");
            return item.getTitle().equals(name);
        }else{
            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "asset name is encrypted.");
            Map<String, String> encryptInfo = new HashMap<>();
            for (Map.Entry<CustomPropertyKey, String> entry : properties.entrySet()) {
                String key = entry.getKey().getKey();
                String value = entry.getValue();
                encryptInfo.put(key, value);
            }
            String clearTitle = decryptAssetName(item.getTitle(), encryptInfo);
            JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "clear title: " + clearTitle);
            return item.getTitle().equals(name);
        }
    }

    //////////////////////// private helper /////////////////////

    // for the master key encryption key
    private void generateSalt(){
        int saltLength = KEYLENGTH / 8; // same size as key output
        mSalt = new byte[saltLength];
        secureRandom.nextBytes(mSalt);
    }
    private void convertPassToKey(String password){
        KeySpec keySpec = new PBEKeySpec(password.toCharArray(), mSalt,
                ITERATIONS, KEYLENGTH);
        SecretKeyFactory keyFactory = null;
        try {
            keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            //TODO: need to notify user
        }
        byte[] keyBytes = new byte[0];
        try {
            keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            //TODO: need to notify user
        }
        mKeyEncryptionKey = new SecretKeySpec(keyBytes, "AES");
    }

    // general encryption
    private Map<String, String> encryptThenBase64(byte[] input, SecretKey key){
        //Map<String, String> values = new HashMap<String, String>();
        Map<String, String> values = new HashMap<String, String>();

        // create cipher
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            // TODO: notify user
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            // TODO: notify user
        }
        final byte[] iv = new byte[cipher.getBlockSize()];
        secureRandom.nextBytes(iv);
        String ivString=Base64.encodeToString(iv, Base64.URL_SAFE);
        IvParameterSpec ivParams = new IvParameterSpec(iv);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            // TODO: notify user
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            // TODO: notify user
        }
        // encrypt
        byte[] ciphertext=null;
        try {
            ciphertext = cipher.doFinal(input);
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
            // TODO: notify user
        } catch (BadPaddingException e) {
            e.printStackTrace();
            // TODO: notify user
        }
        final String encryptedText = Base64.encodeToString(ciphertext, Base64.URL_SAFE);

        values.put(SecureProperties.CIPHER_TEXT.toString(), encryptedText);
        values.put(SecureProperties.CIPHER_TEXT_IV.toString(), ivString);

        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "encrypted text: "+ encryptedText);
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "encrypted text iv: "+ ivString);
        byte[] plainText= decryptStringToData(encryptedText, key, Base64.decode(ivString, Base64.URL_SAFE));
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "plaintext: " + Base64.encodeToString(plainText, Base64.URL_SAFE));

        // test decryption
        return  values;
    }
    private Map<String, String> encryptStringThenBase64(String input, SecretKey key){
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "encrypting: " + input);
        return encryptThenBase64(input.getBytes(), key);
    }
    private Map<String, String> encryptAssetName(String name){
        // generate encryption key
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            //TODO: notify user then exit
        }
        keyGen.init(KEYLENGTH);
        SecretKey secretKey = keyGen.generateKey();

        // encrypt name and get IV
        Map<String, String> cipherAndIV = encryptStringThenBase64(name, secretKey);

        // encrypt the key using the key encryption key
        Map<String, String> encryptedEncryptionKeyandIV = encryptThenBase64(secretKey.getEncoded(), mKeyEncryptionKey);

        Map<String, String> assetInfo = new HashMap<>();

        assetInfo.put(SecureProperties.ASSET_NAME.toString(), cipherAndIV.get(SecureProperties.CIPHER_TEXT.toString()));
        assetInfo.put(SecureProperties.ASSET_NAME_IV.toString(), cipherAndIV.get(SecureProperties.CIPHER_TEXT_IV.toString()));
        assetInfo.put(SecureProperties.ENCRYPTION_KEY.toString(), encryptedEncryptionKeyandIV.get(SecureProperties.CIPHER_TEXT.toString()));
        assetInfo.put(SecureProperties.ENCRYPTION_KEY_IV.toString(), encryptedEncryptionKeyandIV.get(SecureProperties.CIPHER_TEXT_IV.toString()));
        assetInfo.put(SecureProperties.SALT.toString(), Base64.encodeToString(mSalt, Base64.URL_SAFE));

        // test decryption
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "encrypted key : " + assetInfo.get(SecureProperties.ENCRYPTION_KEY.toString()));
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "encrypted key iv : " + assetInfo.get(SecureProperties.ENCRYPTION_KEY_IV.toString()));

        byte[] keyIV = Base64.decode(assetInfo.get(SecureProperties.ENCRYPTION_KEY_IV.toString()), Base64.URL_SAFE);
        byte[] encryptionKeyBytes = decryptStringToData(assetInfo.get(SecureProperties.ENCRYPTION_KEY.toString()), mKeyEncryptionKey, keyIV);
        SecretKey encryptionKey = new SecretKeySpec(encryptionKeyBytes, "AES");

        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "decrypted encryption key: " + Base64.encodeToString(encryptionKey.getEncoded(), Base64.URL_SAFE));
        return assetInfo;
    }

    // general decryption
    private byte[] decryptData(byte[] input, SecretKey key, byte[] iv){
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        IvParameterSpec ivParams = new IvParameterSpec(iv);
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, ivParams);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        byte[] plaintext=null;
        try {
            plaintext = cipher.doFinal(input);
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return plaintext;
    }
    private byte[] decryptStringToData(String input, SecretKey key, byte[] iv){
        return decryptData(Base64.decode(input, Base64.URL_SAFE), key, iv);
    }
    private String decryptStringToString(String input, SecretKey key, byte[] iv){
        return new String(decryptStringToData(input, key, iv));
    }
    private String decryptAssetName(String encryptedName, Map<String, String> encryptInfo){
        // check the salt
        String saltStr = encryptInfo.get(SecureProperties.SALT.toString());
        if (!Base64.encodeToString(mSalt, Base64.URL_SAFE).equals(saltStr)){
            // salt changed, regenerate master key encryption key
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "master key encryption key salt changed!");
            mSalt = Base64.decode(saltStr, Base64.URL_SAFE);
            convertPassToKey("password");
        }else {
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "same salt!");
        }
        // decrypt the encryption key using master key
        byte[] keyIV = Base64.decode(encryptInfo.get(SecureProperties.ENCRYPTION_KEY_IV.toString()), Base64.URL_SAFE);
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "encryption key : " + encryptInfo.get(SecureProperties.ENCRYPTION_KEY.toString()));
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "encryption key iv: " + encryptInfo.get(SecureProperties.ENCRYPTION_KEY_IV.toString()));
        byte[] encryptionKeyBytes = decryptStringToData(encryptInfo.get(SecureProperties.ENCRYPTION_KEY.toString()), mKeyEncryptionKey, keyIV);
        SecretKey encryptionKey = new SecretKeySpec(encryptionKeyBytes, "AES");

        // decrypt the name
        byte[] assetNameIV=Base64.decode(encryptInfo.get(SecureProperties.ASSET_NAME_IV.toString()), Base64.URL_SAFE);
        String assetName = decryptStringToString(encryptedName, encryptionKey, assetNameIV);
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "decrypted asset name: " + assetName);
        return assetName;
    }
}
