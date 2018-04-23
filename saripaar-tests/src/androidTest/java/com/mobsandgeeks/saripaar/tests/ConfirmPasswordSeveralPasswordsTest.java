/*
 * Copyright (C) 2015 Mobs & Geeks
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobsandgeeks.saripaar.tests;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.TextView;

public class ConfirmPasswordSeveralPasswordsTest
        extends ActivityInstrumentationTestCase2<ConfirmPasswordSeveralPasswordsActivity> {

    private TextView mResultTextView;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResultTextView = (TextView) getActivity().findViewById(R.id.resultTextView);
    }

    public void testSeveralPasswordAnnotations_crash() {
        String password = "there_lies_MY-S3creT";
        EspressoHelper.type(R.id.passwordEditText, password);
        EspressoHelper.type(R.id.anotherPasswordEditText, password);
        EspressoHelper.type(R.id.confirmPasswordEditText, password);
        EspressoHelper.clickView(R.id.saripaarButton);
        EspressoHelper.checkForText(Constants.STATE_CRASH, mResultTextView);
    }

    public ConfirmPasswordSeveralPasswordsTest() {
        super(ConfirmPasswordSeveralPasswordsActivity.class);
    }
}
