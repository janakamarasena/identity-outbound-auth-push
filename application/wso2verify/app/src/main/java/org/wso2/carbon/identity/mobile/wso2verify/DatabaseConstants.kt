package org.wso2.carbon.identity.mobile.wso2verify

class DatabaseConstants {
    companion object {
        val DATABASE_NAME = "WSO2Verify.db"
        val DATABASE_VERSION = 1
        val DEVICE_ID = "DEVICE_ID"
        val USERNAME = "USERNAME"
        val TENANT_DOMAIN = "TENANT_DOMAIN"
        val USER_STORE = "USER_STORE"
        val AUTH_URL = "AUTH_URL"
        val PRIVATE_KEY = "PRIVATE_KEY"
        val AUTH_PROFILE_TABLE_NAME = "BIOMETRIC_AUTH_PROFILE"
        val CREATE_DATABASE_QUERY = "CREATE DATABASE $DATABASE_NAME"
        val AUTH_PROFILE_CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS BIOMETRIC_AUTH_PROFILE (" +
                "DEVICE_ID VARCHAR(30) PRIMARY KEY," +
                "USERNAME VARCHAR(50) NOT NULL," +
                "TENANT_DOMAIN VARCHAR(50) NOT NULL," +
                "USER_STORE VARCHAR(50) NOT NULL," +
                "AUTH_URL VARCHAR(100) NOT NULL," +
                "PRIVATE_KEY TEXT NOT NULL" +
                ")"
        val AUTH_PROFILE_DROP_TABLE_QUERY = "DROP TABLE IF EXISTS BIOMETRIC_AUTH_PROFILE"
        val LIST_ALL_PROFILES_QUERY = "SELECT * FROM BIOMETRIC_AUTH_PROFILE"
    }
}
