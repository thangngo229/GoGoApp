package com.example.gogo.utils;

import android.content.Context;
import com.example.gogo.database.AccountDAO;
import com.example.gogo.database.DatabaseHelper;

public class UserUtils {
    public static int getUserId(Context context) {
        String googleId = UserSession.getGoogleId(context);
        if (googleId == null) {
            return -1;
        }

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        AccountDAO accountDAO = new AccountDAO(dbHelper);
        return accountDAO.getUserIdByGoogleId(googleId);
    }
}