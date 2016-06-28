package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.content.DialogInterface;
import android.media.MediaActionSound;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.metadata.CustomPropertyKey;

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
    private SecretKey mKeyEncryptionKey=null;  // must never be stored, and should be cleared on timeout.
    private String mPasswordString=null; // must never be stored, and should be cleared on timeout.
    private byte[] mSalt;               // should be the same for every asset
    private SecureRandom secureRandom;

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
    }

    @Override
    public void listFolderByID (String folderIDStr, final ListFolderByIDCallback callbackInstance){
        super.listFolderByID(folderIDStr, new ListFolderByIDCallback() {
            @Override
            public void callback(FolderInfo info) {
                if (info.items!=null && mPasswordString!=null){
                    for (ItemInfo item : info.items) {
                        Map<CustomPropertyKey, String> properties = item.meta.getCustomProperties();
                        String encryptedEncryptionKey = (String) properties.get(new CustomPropertyKey(SecureProperties.ENCRYPTION_KEY.toString(), CustomPropertyKey.PUBLIC));
                        if (encryptedEncryptionKey == null) {
                            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "asset name is not encrypted.");
                        } else {
                            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "asset name is encrypted.");
                            // decrypt drive asset name
                            Map<String, String> encryptInfo = new HashMap<>();
                            for (Map.Entry<CustomPropertyKey, String> entry : properties.entrySet()) {
                                String key = entry.getKey().getKey();
                                String value = entry.getValue();
                                encryptInfo.put(key, value);
                            }
                            String clearTitle = decryptAssetString(item.meta.getTitle(), encryptInfo.get(SecureProperties.ASSET_NAME_IV.toString()), encryptInfo);
                            item.readableTitle = clearTitle;
                        }
                    }
                }
                callbackInstance.callback(info);
            }
        });
    }

    @Override
    public void createTxtFileInFolder(final String fileName, final String folderIdStr,
                                      final Map<String, String> metaInfo, final ListFolderByIDCallback callbackInstance){
        Map<String, String> cipherData = encryptAssetName(fileName);
        String encryptedName = cipherData.remove(SecureProperties.ASSET_NAME.toString());
        cipherData.put(SecureProperties.SALT.toString(), Base64.encodeToString(mSalt, Base64.URL_SAFE));
        super.createTxtFileInFolder(encryptedName, folderIdStr, cipherData, callbackInstance);
    }

    @Override
    public void readTxtFile(final ItemInfo assetInfo, final ReadTxtFileCallback callbackInstance){
        super.readTxtFile(assetInfo, new ReadTxtFileCallback() {
            @Override
            public void callback(String fileContent) {
                Map<CustomPropertyKey, String> properties = assetInfo.meta.getCustomProperties();
                String cipherIV = (String) properties.get(new CustomPropertyKey(SecureProperties.CIPHER_TEXT_IV.toString(), CustomPropertyKey.PUBLIC));
                if (cipherIV == null || fileContent.length()==0) {
                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "content is not encrypted or zero length." + fileContent);
                    callbackInstance.callback(fileContent);
                }else{
                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "content is encrypted.");
                    Map<String, String> encryptInfo = new HashMap<>();
                    for (Map.Entry<CustomPropertyKey, String> entry : properties.entrySet()) {
                        String key = entry.getKey().getKey();
                        String value = entry.getValue();
                        encryptInfo.put(key, value);
                        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "key: "+key+" value: "+value);
                    }
                    String clearFileContent = decryptAssetString(fileContent, encryptInfo.get(SecureProperties.CIPHER_TEXT_IV.toString()), encryptInfo);
                    callbackInstance.callback(clearFileContent);
                }
            }
        });
    }

    @Override
    public void writeTxtFile(final ItemInfo assetInfo, final String contentStr, final WriteTxtFileCallback callbackInstance, final Map<String, String> metaInfo) {
        Map<CustomPropertyKey, String> properties = assetInfo.meta.getCustomProperties();
        String encryptedEncryptionKey = (String) properties.get(new CustomPropertyKey(SecureProperties.ENCRYPTION_KEY.toString(), CustomPropertyKey.PUBLIC));
        if (encryptedEncryptionKey == null) {
            // clear content
            super.writeTxtFile(assetInfo, contentStr, callbackInstance, null);
        } else {
            // encrypt the content first
            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "encrypting content: " + contentStr);
            Map<String, String> encryptInfo = new HashMap<>();
            for (Map.Entry<CustomPropertyKey, String> entry : properties.entrySet()) {
                String key = entry.getKey().getKey();
                String value = entry.getValue();
                JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "key: "+key+" value: "+value);
                encryptInfo.put(key, value);
            }
            String encryptedFileContent = encryptAssetString(contentStr, encryptInfo);
            super.writeTxtFile(assetInfo, encryptedFileContent, callbackInstance, encryptInfo);
        }
    }

    @Override
    public void createFolderInFolder(final String name, final String folderIdStr, final boolean gotoFolder,
                                     final Map<String, String> metaInfo, final ListFolderByIDCallback callbackInstance){
        Map<String, String> cipherData = encryptAssetName(name);
        String encryptedName = cipherData.remove(SecureProperties.ASSET_NAME.toString());
        cipherData.put(SecureProperties.SALT.toString(), Base64.encodeToString(mSalt, Base64.URL_SAFE));
        super.createFolderInFolder(encryptedName, folderIdStr, gotoFolder, cipherData, callbackInstance);
    }

    @Override
    protected void initAppRoot(final ListFolderByIDCallback callbackInstance){
        final String driveRoot = Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId().encodeToString();
        String name = mParentActivity.getString(R.string.app_name);
        super.createFolderInFolder(name, driveRoot, true, null, new ListFolderByIDCallback(){
            @Override
            public void callback(FolderInfo info) {
                // init mAppRootFolder
                mAppRootFolder = info.folder;

                // setup master key
                Metadata encryptedItem=null;
                if (info.items.length!=0) {
                    for (ItemInfo item : info.items) {
                        Map<CustomPropertyKey, String> properties = item.meta.getCustomProperties();
                        String encryptedEncryptionKey = (String) properties.get(new CustomPropertyKey(SecureProperties.ENCRYPTION_KEY.toString(), CustomPropertyKey.PUBLIC));
                        if (encryptedEncryptionKey != null) {
                            // found encrypted item
                            encryptedItem = item.meta;
                            break;
                        }
                    }
                }
                if (encryptedItem!=null){
                    passwordPrompt(encryptedItem);
                }else{
                    newPasswordPrompt();
                }
                callbackInstance.callback(info);
            }
        });
    }

    @Override
    protected boolean nameCompare(String name, Metadata item, Map<String, String> metaInfo){
        Map<CustomPropertyKey, String> properties = item.getCustomProperties();
        String encryptedEncryptionKey = (String) properties.get(new CustomPropertyKey(SecureProperties.ENCRYPTION_KEY.toString(), CustomPropertyKey.PUBLIC));
        if (encryptedEncryptionKey==null) {
            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "asset name is not encrypted.");
            return item.getTitle().equals(name);
        }else{
            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "asset name is encrypted.");
            // decrypt drive asset name
            Map<String, String> encryptInfo = new HashMap<>();
            for (Map.Entry<CustomPropertyKey, String> entry : properties.entrySet()) {
                String key = entry.getKey().getKey();
                String value = entry.getValue();
                encryptInfo.put(key, value);
            }
            String clearTitle = decryptAssetString(item.getTitle(), encryptInfo.get(SecureProperties.ASSET_NAME_IV.toString()), encryptInfo);

            // decrypt input name
            String clearInputTitle = decryptAssetString(name, metaInfo.get(SecureProperties.ASSET_NAME_IV.toString()), metaInfo);

            return clearTitle.equals(clearInputTitle);
        }
    }

    //////////////////////// private helper /////////////////////

    // password prompt
    private void passwordPrompt(final Metadata validationFile){
        AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
        builder.setTitle("Enter Password");
        final EditText password = new EditText(mParentActivity);
        password.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(password);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "OK clicked...");
                String localPassword = password.getText().toString();
                mPasswordString=localPassword;
                mKeyEncryptionKey=null;
                mSalt=null;

                // validate the password
                if (passwordValidation(validationFile)) {
                    setChanged();
                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "observers notified.");
                    notifyObservers();
                    clearChanged();
                    mConnected = true;
                }else {
                    passwordPrompt(validationFile);
                }
            }
        });
        builder.show();
    }

    // validate password
    private boolean passwordValidation(Metadata validationFile){
        Map<CustomPropertyKey, String> properties = validationFile.getCustomProperties();
        Map<String, String> encryptInfo = new HashMap<>();
        for (Map.Entry<CustomPropertyKey, String> entry : properties.entrySet()) {
            String key = entry.getKey().getKey();
            String value = entry.getValue();
            encryptInfo.put(key, value);
        }
        String clearTitle = decryptAssetString(validationFile.getTitle(), encryptInfo.get(SecureProperties.ASSET_NAME_IV.toString()), encryptInfo);
        if (clearTitle!=null){
            // success?
            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "password validated.");
            return true;
        }else{
            // try again
            JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "password incorrect, try again.");
            return false;
        }
    }

    // new password prompt
    private void newPasswordPrompt(){
        LinearLayout passLayout = new LinearLayout(mParentActivity);
        passLayout.setOrientation(LinearLayout.VERTICAL);
        final EditText password = new EditText(mParentActivity);
        password.setHint("new password");
        passLayout.addView(password);
        final EditText passwordAgain = new EditText(mParentActivity);
        passwordAgain.setHint("password again");
        passLayout.addView(passwordAgain);

        AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
        builder.setTitle("New Password");
        builder.setView(passLayout);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "OK clicked...");
                // check if password match each other
                if (!password.getText().toString().equals(passwordAgain.getText().toString())){
                    newPasswordPrompt();
                }else{
                    mPasswordString=password.getText().toString();
                    mKeyEncryptionKey=null;
                    mSalt=null;
                    // generate salt and create a validation file
                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "Generating password validation file.");
                    generateSalt();
                    convertPassToKey(mPasswordString);
                    createTxtFileInFolder("passwordValidationFile", mAppRootFolder.getDriveId().encodeToString(),
                            new ListFolderByIDCallback() {
                                @Override
                                public void callback(FolderInfo info) {
                                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "password validation file generated.");
                                    setChanged();
                                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "observers notified.");
                                    notifyObservers();
                                    clearChanged();
                                    mConnected = true;
                                }
                            });

                }
            }
        });
        builder.show();
    }

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

        return  values;
    }
    private Map<String, String> encryptStringThenBase64(String input, SecretKey key){
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

        return assetInfo;
    }
    private String encryptAssetString(String clearAssetString, Map<String, String> encryptInfo){
        // check the salt
        String saltStr = encryptInfo.get(SecureProperties.SALT.toString());
        if (!Base64.encodeToString(mSalt, Base64.URL_SAFE).equals(saltStr)){
            // salt changed, regenerate master key encryption key
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "master key encryption key salt changed!");
            mSalt = Base64.decode(saltStr, Base64.URL_SAFE);
            convertPassToKey(mPasswordString);
        }else {
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "same salt!");
        }
        // decrypt the encryption key using master key
        byte[] keyIV = Base64.decode(encryptInfo.get(SecureProperties.ENCRYPTION_KEY_IV.toString()), Base64.URL_SAFE);
        byte[] encryptionKeyBytes = decryptStringToData(encryptInfo.get(SecureProperties.ENCRYPTION_KEY.toString()), mKeyEncryptionKey, keyIV);
        SecretKey encryptionKey = new SecretKeySpec(encryptionKeyBytes, "AES");

        // encrypt the asset string and get IV
        Map<String, String> cipherAndIV = encryptStringThenBase64(clearAssetString, encryptionKey);

        // add the asset IV to the encryptInfo map
        encryptInfo.put(SecureProperties.CIPHER_TEXT_IV.toString(), cipherAndIV.get(SecureProperties.CIPHER_TEXT_IV.toString()));

        // return the encrypted string
        return cipherAndIV.get(SecureProperties.CIPHER_TEXT.toString());
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
    private String decryptAssetString(String encryptedString, String iv, Map<String, String> encryptInfo){
        // check the salt
        String saltStr = encryptInfo.get(SecureProperties.SALT.toString());
        if (mSalt==null || !Base64.encodeToString(mSalt, Base64.URL_SAFE).equals(saltStr)){
            // salt changed, regenerate master key encryption key
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "master key encryption key salt changed!");
            mSalt = Base64.decode(saltStr, Base64.URL_SAFE);
            convertPassToKey(mPasswordString);
        }else {
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "same salt!");
        }
        // decrypt the encryption key using master key
        byte[] keyIV = Base64.decode(encryptInfo.get(SecureProperties.ENCRYPTION_KEY_IV.toString()), Base64.URL_SAFE);
        byte[] encryptionKeyBytes = decryptStringToData(encryptInfo.get(SecureProperties.ENCRYPTION_KEY.toString()), mKeyEncryptionKey, keyIV);
        if (encryptionKeyBytes==null){
            return null;
        }
        SecretKey encryptionKey = new SecretKeySpec(encryptionKeyBytes, "AES");

        // decrypt the name
        byte[] assetStringIV=Base64.decode(iv, Base64.URL_SAFE);
        String assetString = decryptStringToString(encryptedString, encryptionKey, assetStringIV);
        return assetString;
    }
}
