/*
 * Copyright (C) 2014 Mobs & Geeks
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

package com.mobsandgeeks.saripaar;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.mobsandgeeks.saripaar.adapter.CheckBoxBooleanAdapter;
import com.mobsandgeeks.saripaar.adapter.RadioButtonBooleanAdapter;
import com.mobsandgeeks.saripaar.adapter.RadioGroupBooleanAdapter;
import com.mobsandgeeks.saripaar.adapter.SpinnerIndexAdapter;
import com.mobsandgeeks.saripaar.adapter.ViewDataAdapter;
import com.mobsandgeeks.saripaar.annotation.AssertFalse;
import com.mobsandgeeks.saripaar.annotation.AssertTrue;
import com.mobsandgeeks.saripaar.annotation.Checked;
import com.mobsandgeeks.saripaar.annotation.ConfirmEmail;
import com.mobsandgeeks.saripaar.annotation.ConfirmPassword;
import com.mobsandgeeks.saripaar.annotation.CreditCard;
import com.mobsandgeeks.saripaar.annotation.DecimalMax;
import com.mobsandgeeks.saripaar.annotation.DecimalMin;
import com.mobsandgeeks.saripaar.annotation.Digits;
import com.mobsandgeeks.saripaar.annotation.Domain;
import com.mobsandgeeks.saripaar.annotation.Email;
import com.mobsandgeeks.saripaar.annotation.Future;
import com.mobsandgeeks.saripaar.annotation.IpAddress;
import com.mobsandgeeks.saripaar.annotation.Isbn;
import com.mobsandgeeks.saripaar.annotation.Length;
import com.mobsandgeeks.saripaar.annotation.Max;
import com.mobsandgeeks.saripaar.annotation.Min;
import com.mobsandgeeks.saripaar.annotation.NotEmpty;
import com.mobsandgeeks.saripaar.annotation.Optional;
import com.mobsandgeeks.saripaar.annotation.Order;
import com.mobsandgeeks.saripaar.annotation.Password;
import com.mobsandgeeks.saripaar.annotation.Past;
import com.mobsandgeeks.saripaar.annotation.Pattern;
import com.mobsandgeeks.saripaar.annotation.Select;
import com.mobsandgeeks.saripaar.annotation.Url;
import com.mobsandgeeks.saripaar.annotation.ValidateUsing;
import com.mobsandgeeks.saripaar.exception.ConversionException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link Validator} takes care of validating the
 * {@link View}s in the given controller instance. Usually, an
 * {@link Activity} or a {@link Fragment}. However, it can also be used
 * with other controller classes that contain references to {@link View} objects.
 * <p>
 * The {@link Validator} is capable of performing validations in two
 * modes,
 * <ol>
 * <li>{@link Mode#BURST}, where all the views are validated and all errors are reported
 * via the callback at once. Fields need not be ordered using the
 * {@link Order} annotation in {@code BURST} mode.
 * </li>
 * <li>{@link Mode#IMMEDIATE}, in which the validation stops and the error is reported as soon
 * as a {@link Rule} fails. To use this mode, the fields SHOULD
 * BE ordered using the {@link Order} annotation.
 * </li>
 * </ol>
 * <p>
 * There are three flavors of the {@code validate()} method.
 * <ol>
 * <li>{@link #validate()}, no frills regular validation that validates all
 * {@link View}s.
 * </li>
 * </li>
 * </ol>
 * <p>
 * The {@link Validator} requires a
 * {@link Validator.ValidationListener} that reports the outcome of the
 * validation.
 * <ul>
 * <li> {@link Validator.ValidationListener#onValidationSucceeded()}
 * is called if all {@link Rule}s pass.
 * </li>
 * <li>
 * The {@link Validator.ValidationListener#onValidationFailed(List)}
 * callback reports errors caused by failures. In {@link Mode#IMMEDIATE} this callback will
 * contain just one instance of the {@link ValidationError}
 * object.
 * </li>
 * </ul>
 *
 * @author Ragunath Jawahar {@literal <rj@mobsandgeeks.com>}
 * @since 1.0
 */
@SuppressWarnings({"unchecked", "ForLoopReplaceableByForEach"})
public class Validator {

    // Entries are registered inside a static block (Placed at the end of source)
    private static final Registry SARIPAAR_REGISTRY = new Registry();

    // Holds adapter entries that are mapped to corresponding views.
    private final
    Map<Class<? extends View>, HashMap<Class<?>, ViewDataAdapter>> mRegisteredAdaptersMap =
            new HashMap<Class<? extends View>, HashMap<Class<?>, ViewDataAdapter>>();

    // Attributes
    private Object mController;
    private Mode mValidationMode;
    private ValidationContext mValidationContext;
    private Map<Field, ArrayList<Pair<Rule, ViewDataAdapter>>> mRulesMap;
    private Map<Field, ArrayList<Pair<Annotation, ViewDataAdapter>>> mOptionalViewsMap;
    private boolean mOrderedFields;
    private boolean mValidateInvisibleViews;
    private SequenceComparator mSequenceComparator;
    private ViewValidatedAction mViewValidatedAction;
    private Handler mViewValidatedActionHandler;
    private ValidationListener mValidationListener;
    private AsyncValidationTask mAsyncValidationTask;

    /**
     * Constructor.
     *
     * @param controller The class containing {@link View}s to be validated. Usually,
     *                   an {@link Activity} or a {@link Fragment}.
     */
    public Validator(final Context context, final Object controller) {
        assertNotNull(controller, "controller");
        mController = controller;
        mValidationMode = Mode.BURST;
        mSequenceComparator = new SequenceComparator();
        mViewValidatedAction = new DefaultViewValidatedAction();
        if (context != null) {
            mValidationContext = new ValidationContext(context, mController);
        }
    }

    public Validator(final Object controller) {
        this(null, controller);

        Context context = null;
        if (controller instanceof Activity) {
            context = (Context) controller;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (controller instanceof Fragment) {
                context = ((Fragment) controller).getActivity();
            }
        }

        setContext(context);
    }

    public void setContext(Context context) {
        if (mValidationContext == null && context != null) {
            mValidationContext = new ValidationContext(context, mController);
        }
    }

    /**
     * A convenience method for registering {@link Rule} annotations that
     * act on {@link android.widget.TextView} and it's children, the most notable one being
     * {@link android.widget.EditText}. Register custom annotations for
     * {@link android.widget.TextView}s that validates {@link Double},
     * {@link Float}, {@link Integer} and {@link String} types.
     * <p>
     * For registering rule annotations for other view types see,
     * {@link #registerAdapter(Class, ViewDataAdapter)}.
     *
     * @param ruleAnnotation A rule {@link Annotation}.
     */
    public static void registerAnnotation(final Class<? extends Annotation> ruleAnnotation) {
        SARIPAAR_REGISTRY.register(ruleAnnotation);
    }

    /**
     * An elaborate method for registering custom rule annotations.
     *
     * @param annotation      The annotation that you want to register.
     * @param viewType        The {@link View} type.
     * @param viewDataAdapter An instance of the
     *                        {@link ViewDataAdapter} for your
     *                        {@link View}.
     * @param <VIEW>          The {@link View} for which the
     *                        {@link Annotation} and
     *                        {@link ViewDataAdapter} is being registered.
     */
    public static <VIEW extends View> void registerAnnotation(
            final Class<? extends Annotation> annotation, final Class<VIEW> viewType,
            final ViewDataAdapter<VIEW, ?> viewDataAdapter) {

        ValidateUsing validateUsing = annotation.getAnnotation(ValidateUsing.class);
        Class ruleDataType = Reflector.getRuleDataType(validateUsing);
        SARIPAAR_REGISTRY.register(viewType, ruleDataType, viewDataAdapter, annotation);
    }

    /**
     * Registers a {@link ViewDataAdapter} for the given
     * {@link View}.
     *
     * @param viewType        The {@link View} for which a
     *                        {@link ViewDataAdapter} is being registered.
     * @param viewDataAdapter A {@link ViewDataAdapter} instance.
     * @param <VIEW>          The {@link View} type.
     * @param <DATA_TYPE>     The {@link ViewDataAdapter} type.
     */
    public <VIEW extends View, DATA_TYPE> void registerAdapter(
            final Class<VIEW> viewType, final ViewDataAdapter<VIEW, DATA_TYPE> viewDataAdapter) {
        assertNotNull(viewType, "viewType");
        assertNotNull(viewDataAdapter, "viewDataAdapter");

        HashMap<Class<?>, ViewDataAdapter> dataTypeAdapterMap = mRegisteredAdaptersMap.get(viewType);
        if (dataTypeAdapterMap == null) {
            dataTypeAdapterMap = new HashMap<Class<?>, ViewDataAdapter>();
            mRegisteredAdaptersMap.put(viewType, dataTypeAdapterMap);
        }

        // Find adapter's data type
        Method getDataMethod = Reflector.findGetDataMethod(viewDataAdapter.getClass());
        Class<?> adapterDataType = getDataMethod.getReturnType();

        dataTypeAdapterMap.put(adapterDataType, viewDataAdapter);
    }

    /**
     * Set a {@link Validator.ValidationListener} to the
     * {@link Validator}.
     *
     * @param validationListener A {@link Validator.ValidationListener}
     *                           instance. null throws an {@link IllegalArgumentException}.
     */
    public void setValidationListener(final ValidationListener validationListener) {
        assertNotNull(validationListener, "validationListener");
        this.mValidationListener = validationListener;
    }

    /**
     * Set a {@link Validator.ViewValidatedAction} to the
     * {@link Validator}.
     *
     * @param viewValidatedAction A {@link Validator.ViewValidatedAction}
     *                            instance.
     */
    public void setViewValidatedAction(final ViewValidatedAction viewValidatedAction) {
        this.mViewValidatedAction = viewValidatedAction;
    }

    /**
     * Set the validation {@link Validator.Mode} for the current
     * {@link Validator} instance.
     *
     * @param validationMode {@link Mode#BURST} or {@link Mode#IMMEDIATE}, null throws an
     *                       {@link IllegalArgumentException}.
     */
    public void setValidationMode(final Mode validationMode) {
        assertNotNull(validationMode, "validationMode");
        this.mValidationMode = validationMode;
    }

    /**
     * Gets the current {@link Validator.Mode}.
     *
     * @return The current validation mode of the {@link Validator}.
     */
    public Mode getValidationMode() {
        return mValidationMode;
    }

    /**
     * Configures the validator to validate invisible views.
     *
     * @param validate {@code true} includes invisible views during validation.
     */
    public void validateInvisibleViews(final boolean validate) {
        this.mValidateInvisibleViews = validate;
    }

    /**
     * Validates all {@link View}s with {@link Rule}s.
     * When validating in {@link Validator.Mode#IMMEDIATE}, all
     * {@link View} fields must be ordered using the
     * {@link Order} annotation.
     */
    public void validate() {
        validate(false);
    }

    /**
     * Validates all {@link View}s with {@link Rule}s.
     * When validating in {@link Validator.Mode#IMMEDIATE}, all
     * {@link View} fields must be ordered using the
     * {@link Order} annotation. Asynchronous calls will cancel
     * any pending or ongoing asynchronous validation and start a new one.
     *
     * @param async true if asynchronous, false otherwise.
     */
    public void validate(final boolean async) {
        createRulesSafelyAndLazily(false);

        Field lastView = getLastField();
        if (Mode.BURST.equals(mValidationMode)) {
            validateUnorderedFieldsWithCallbackTill(lastView, async);
        } else if (Mode.IMMEDIATE.equals(mValidationMode)) {
            String reasonSuffix = String.format("in %s mode.", Mode.IMMEDIATE.toString());
            validateOrderedFieldsWithCallbackTill(lastView, reasonSuffix, async);
        } else {
            throw new RuntimeException("This should never happen!");
        }
    }

    /**
     * Used to find if an asynchronous validation task is running. Useful only when you run the
     * {@link Validator} in asynchronous mode.
     *
     * @return true if the asynchronous task is running, false otherwise.
     */
    public boolean isValidating() {
        return mAsyncValidationTask != null
                && mAsyncValidationTask.getStatus() != AsyncTask.Status.FINISHED;
    }

    /**
     * Cancels a running asynchronous validation task.
     *
     * @return true if a running asynchronous task was cancelled, false otherwise.
     */
    public boolean cancelAsync() {
        boolean cancelled = false;
        if (mAsyncValidationTask != null) {
            cancelled = mAsyncValidationTask.cancel(true);
            mAsyncValidationTask = null;
        }

        return cancelled;
    }

    /**
     * Add one or more {@link QuickRule}s for a {@link View}.
     *
     * @param view       A {@link View} for which
     *                   {@link QuickRule}(s) are to be added.
     * @param quickRules Varargs of {@link QuickRule}s.
     * @param <VIEW>     The {@link View} type for which the
     *                   {@link QuickRule}s are being registered.
     *                   //TODO 兼容注册快速规则
     */
//    public <VIEW extends View> void put(final VIEW view, final QuickRule<VIEW>... quickRules) {
//        assertNotNull(view, "view");
//        assertNotNull(quickRules, "quickRules");
//        if (quickRules.length == 0) {
//            throw new IllegalArgumentException("'quickRules' cannot be empty.");
//        }
//
//        // Create rules
//        createRulesSafelyAndLazily(true);
//
//        // If all fields are ordered, then this field should be ordered too
//        if (mOrderedFields && !mRulesMap.containsKey(view)) {
//            String message = String.format("All fields are ordered, so this `%s` should be "
//                    + "ordered too, declare the view as a field and add the `@Order` "
//                    + "annotation.", view.getClass().getName());
//            throw new IllegalStateException(message);
//        }
//
//        // If there are no rules, create an empty list
//        ArrayList<Pair<Rule, ViewDataAdapter>> ruleAdapterPairs = mRulesMap.get(view);
//        ruleAdapterPairs = ruleAdapterPairs == null
//                ? new ArrayList<Pair<Rule, ViewDataAdapter>>() : ruleAdapterPairs;
//
//        // Add the quick rule to existing rules
//        for (int i = 0, n = quickRules.length; i < n; i++) {
//            QuickRule quickRule = quickRules[i];
//            if (quickRule != null) {
//                ruleAdapterPairs.add(new Pair(quickRule, null));
//            }
//        }
//        Collections.sort(ruleAdapterPairs, mSequenceComparator);
//        mRulesMap.put(view, ruleAdapterPairs);
//    }

    /**
     * Remove all {@link Rule}s for the given {@link View}.
     *
     * @param view The {@link View} whose rules should be removed.
     */
    public void removeRules(final View view) {
        assertNotNull(view, "view");
        if (mRulesMap == null) {
            createRulesSafelyAndLazily(false);
        }
        mRulesMap.remove(view);
    }

    static boolean isSaripaarAnnotation(final Class<? extends Annotation> annotation) {
        return SARIPAAR_REGISTRY.getRegisteredAnnotations().contains(annotation);
    }

    /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *  Private Methods
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */
    private static void assertNotNull(final Object object, final String argumentName) {
        if (object == null) {
            String message = String.format("'%s' cannot be null.", argumentName);
            throw new IllegalArgumentException(message);
        }
    }

    private void createRulesSafelyAndLazily(final boolean addingQuickRules) {
        // Create rules lazily, because we don't have to worry about the order of
        // instantiating the Validator.
        if (mRulesMap == null) {
            final List<Field> annotatedFields = getSaripaarAnnotatedFields(mController.getClass());
            mRulesMap = createRules(annotatedFields);
            mValidationContext.setViewRulesMap(mRulesMap);
        }

        if (!addingQuickRules && mRulesMap.size() == 0) {
            String message = "No rules found. You must have at least one rule to validate. "
                    + "If you are using custom annotations, make sure that you have registered "
                    + "them using the 'Validator.register()' method.";
            throw new IllegalStateException(message);
        }
    }

    private List<Field> getSaripaarAnnotatedFields(final Class<?> controllerClass) {
        Set<Class<? extends Annotation>> saripaarAnnotations =
                SARIPAAR_REGISTRY.getRegisteredAnnotations();

        List<Field> annotatedFields = new ArrayList<Field>();
        Class cls = controllerClass;
        while (cls != null && !"java.lang.Object".equals(cls.getCanonicalName())) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                if (isSaripaarAnnotatedField(field, saripaarAnnotations)) {
                    annotatedFields.add(field);
                }
            }
            cls = cls.getSuperclass();
        }

        // Sort
        SaripaarFieldsComparator comparator = new SaripaarFieldsComparator();
        Collections.sort(annotatedFields, comparator);
        mOrderedFields = annotatedFields.size() == 1
                ? annotatedFields.get(0).getAnnotation(Order.class) != null
                : annotatedFields.size() != 0 && comparator.areOrderedFields();

        return annotatedFields;
    }


    private boolean isSaripaarAnnotatedField(final Field field,
                                             final Set<Class<? extends Annotation>> registeredAnnotations) {
        boolean hasOrderAnnotation = field.getAnnotation(Order.class) != null;
        boolean hasSaripaarAnnotation = false;

        if (!hasOrderAnnotation) {
            Annotation[] annotations = field.getAnnotations();
            for (int i = 0, n = annotations.length; i < n; i++) {
                Annotation annotation = annotations[i];
                hasSaripaarAnnotation = registeredAnnotations.contains(annotation.annotationType());
                if (hasSaripaarAnnotation) {
                    break;
                }
            }
        }

        return hasOrderAnnotation || hasSaripaarAnnotation;
    }

    private Map<Field, ArrayList<Pair<Rule, ViewDataAdapter>>> createRules(
            final List<Field> annotatedFields) {

        final Map<Field, ArrayList<Pair<Rule, ViewDataAdapter>>> viewRulesMap =
                new LinkedHashMap<Field, ArrayList<Pair<Rule, ViewDataAdapter>>>();

        for (int i = 0, n = annotatedFields.size(); i < n; i++) {
            Field field = annotatedFields.get(i);
            final ArrayList<Pair<Rule, ViewDataAdapter>> ruleAdapterPairs =
                    new ArrayList<Pair<Rule, ViewDataAdapter>>();
            final Annotation[] fieldAnnotations = field.getAnnotations();

            // @Optional
            final boolean hasOptionalAnnotation = hasOptionalAnnotation(fieldAnnotations);
            if (hasOptionalAnnotation && mOptionalViewsMap == null) {
                mOptionalViewsMap = new HashMap<Field,
                        ArrayList<Pair<Annotation, ViewDataAdapter>>>();
            }

            for (int j = 0, nAnnotations = fieldAnnotations.length; j < nAnnotations; j++) {
                Annotation annotation = fieldAnnotations[j];
                if (isSaripaarAnnotation(annotation.annotationType())) {
                    Pair<Rule, ViewDataAdapter> ruleAdapterPair =
                            getRuleAdapterPair(annotation, field);
                    ruleAdapterPairs.add(ruleAdapterPair);

                    // @Optional
                    if (hasOptionalAnnotation) {
                        ArrayList<Pair<Annotation, ViewDataAdapter>> pairs =
                                mOptionalViewsMap.get(field);
                        if (pairs == null) {
                            pairs = new ArrayList<Pair<Annotation, ViewDataAdapter>>();
                        }
                        pairs.add(new Pair(annotation, ruleAdapterPair.second));
                        mOptionalViewsMap.put(field, pairs);
                    }
                }
            }

            Collections.sort(ruleAdapterPairs, mSequenceComparator);
            viewRulesMap.put(field, ruleAdapterPairs);
        }

        return viewRulesMap;
    }

    private boolean hasOptionalAnnotation(final Annotation[] annotations) {
        if (annotations != null && annotations.length > 0) {
            for (int i = 0, n = annotations.length; i < n; i++) {
                if (Optional.class.equals(annotations[i].annotationType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Pair<Rule, ViewDataAdapter> getRuleAdapterPair(final Annotation saripaarAnnotation,
                                                           final Field field) {
        final Class<? extends Annotation> annotationType = saripaarAnnotation.annotationType();
        final Class<?> viewFieldType = field.getType();
        final Class<?> ruleDataType = Reflector.getRuleDataType(saripaarAnnotation);

        final ViewDataAdapter dataAdapter = Reflector.isViewField(field) ? getDataAdapter(annotationType, viewFieldType,
                ruleDataType) : null;

        final Class<? extends AnnotationRule> ruleType = getRuleType(saripaarAnnotation);
        final AnnotationRule rule = Reflector.instantiateRule(ruleType,
                saripaarAnnotation, mValidationContext);

        return new Pair<Rule, ViewDataAdapter>(rule, dataAdapter);
    }

    private ViewDataAdapter getDataAdapter(final Class<? extends Annotation> annotationType,
                                           final Class<?> viewFieldType, final Class<?> adapterDataType) {

        // Get an adapter from the stock registry
        ViewDataAdapter dataAdapter = SARIPAAR_REGISTRY.getDataAdapter(
                annotationType, (Class) viewFieldType);

        // If we are unable to find a Saripaar stock adapter, check the registered adapters
        if (dataAdapter == null) {
            HashMap<Class<?>, ViewDataAdapter> dataTypeAdapterMap =
                    mRegisteredAdaptersMap.get(viewFieldType);
            dataAdapter = dataTypeAdapterMap != null
                    ? dataTypeAdapterMap.get(adapterDataType)
                    : null;
        }

        return dataAdapter;
    }

    private Class<? extends AnnotationRule> getRuleType(final Annotation ruleAnnotation) {
        ValidateUsing validateUsing = ruleAnnotation.annotationType()
                .getAnnotation(ValidateUsing.class);
        return validateUsing != null ? validateUsing.value() : null;
    }


    private void validateUnorderedFieldsWithCallbackTill(final Field field, final boolean async) {
        validateFieldsWithCallbackTill(field, false, null, async);
    }

    private void validateOrderedFieldsWithCallbackTill(final Field field, final String reasonSuffix,
                                                       final boolean async) {
        validateFieldsWithCallbackTill(field, true, reasonSuffix, async);
    }

    private void validateFieldsWithCallbackTill(final Field field, final boolean orderedFields,
                                                final String reasonSuffix, final boolean async) {
        createRulesSafelyAndLazily(false);
        if (async) {
            if (mAsyncValidationTask != null) {
                mAsyncValidationTask.cancel(true);
            }
            mAsyncValidationTask = new AsyncValidationTask(field, orderedFields, reasonSuffix);
            mAsyncValidationTask.execute((Void[]) null);
        } else {
            triggerValidationListenerCallback(validateTill(field, orderedFields, reasonSuffix));
        }
    }

    private synchronized ValidationReport validateTill(final Field field,
                                                       final boolean requiresOrderedRules, final String reasonSuffix) {
        // Do we need ordered rules?
        if (requiresOrderedRules) {
            assertOrderedFields(mOrderedFields, reasonSuffix);
        }

        // Have we registered a validation listener?
        assertNotNull(mValidationListener, "validationListener");

        // Everything good. Bingo! validate ;)
        return getValidationReport(field, mRulesMap, mValidationMode);
    }

    private void triggerValidationListenerCallback(final ValidationReport validationReport) {
        final List<ValidationError> validationErrors = validationReport.errors;

        if (validationErrors.size() == 0 && !validationReport.hasMoreErrors) {
            mValidationListener.onValidationSucceeded();
        } else {
            mValidationListener.onValidationFailed(validationErrors);
        }
    }

    private void assertOrderedFields(final boolean orderedRules, final String reasonSuffix) {
        if (!orderedRules) {
            String message = "Rules are unordered, all view fields should be ordered "
                    + "using the '@Order' annotation " + reasonSuffix;
            throw new IllegalStateException(message);
        }
    }

    private ValidationReport getValidationReport(final Field targetField,
                                                 final Map<Field, ArrayList<Pair<Rule, ViewDataAdapter>>> viewRulesMap,
                                                 final Mode validationMode) {

        final List<ValidationError> validationErrors = new ArrayList<ValidationError>();
        final Set<Field> fields = viewRulesMap.keySet();

        // Don't add errors for fields that are placed after the specified view in validateTill()
        boolean addErrorToReport = targetField != null;

        // Does the form have more errors? Used in validateTill()
        boolean hasMoreErrors = false;

        validation:
        for (Field field : fields) {
            List<Pair<Rule, ViewDataAdapter>> ruleAdapterPairs = viewRulesMap.get(field);

            // @Optional
            boolean isOptional = mOptionalViewsMap != null && mOptionalViewsMap.containsKey(field);
            if (isOptional && containsOptionalValue(field)) {
                continue;
            }

            // Validate all the rules for the given view.
            List<Rule> failedRules = null;
            for (int i = 0, nRules = ruleAdapterPairs.size(); i < nRules; i++) {
                Pair<Rule, ViewDataAdapter> ruleAdapterPair = ruleAdapterPairs.get(i);
                Rule failedRule = validateFieldWithRule(
                        field, ruleAdapterPair.first, ruleAdapterPair.second);
                boolean isLastRuleForView = i + 1 == nRules;

                if (failedRule != null) {
                    if (addErrorToReport) {
                        if (failedRules == null) {
                            failedRules = new ArrayList<Rule>();
                            validationErrors.add(new ValidationError(field, failedRules));
                        }
                        failedRules.add(failedRule);
                    } else {
                        hasMoreErrors = true;
                    }

                    if (Mode.IMMEDIATE.equals(validationMode) && isLastRuleForView) {
                        break validation;
                    }
                }

                // Don't add reports for subsequent fields
                if (field.equals(targetField) && isLastRuleForView) {
                    addErrorToReport = false;
                }
            }

            // Callback if a view passes all rules
            boolean viewPassedAllRules = (failedRules == null || failedRules.size() == 0)
                    && !hasMoreErrors;
            if (viewPassedAllRules && mViewValidatedAction != null) {
                triggerViewValidatedCallback(mViewValidatedAction, Reflector.getFieldValue(mController, field));
            }
        }

        return new ValidationReport(validationErrors, hasMoreErrors);
    }

    private boolean containsOptionalValue(final Field field) {
        ArrayList<Pair<Annotation, ViewDataAdapter>> annotationAdapterPairs
                = mOptionalViewsMap.get(field);

        for (int i = 0, n = annotationAdapterPairs.size(); i < n; i++) {
            Pair<Annotation, ViewDataAdapter> pair = annotationAdapterPairs.get(i);
            ViewDataAdapter adapter = pair.second;
            Annotation ruleAnnotation = pair.first;
            View view = Reflector.getViewByField(mController, field);
            if (view != null && adapter != null && adapter.containsOptionalValue(view, ruleAnnotation)) {
                return true;
            }
        }

        return false;
    }

    private Rule validateFieldWithRule(final Field field, final Rule rule,
                                       final ViewDataAdapter dataAdapter) {

        boolean valid = false;
        boolean isView = Reflector.isViewField(field);
        Object fieldValue = Reflector.getFieldValue(mController, field);
        if (rule instanceof AnnotationRule) {
            Object data;
            if (isView) {
                View view = (View) fieldValue;
                try {
                    data = dataAdapter.getData(view);
                    valid = rule.isValid(data);
                } catch (ConversionException e) {
                    valid = false;
                    e.printStackTrace();
                }
            } else {
                valid = rule.isValid(fieldValue);
            }

        } else if (rule instanceof QuickRule && isView) {
            valid = rule.isValid(fieldValue);
        }

        return valid ? null : rule;
    }

    private void triggerViewValidatedCallback(final ViewValidatedAction viewValidatedAction, final Object obj) {
        boolean isOnMainThread = Looper.myLooper() == Looper.getMainLooper();
        if (isOnMainThread) {
            viewValidatedAction.onAllRulesPassed(obj);
        } else {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    viewValidatedAction.onAllRulesPassed(obj);
                }
            });
        }
    }

    private void runOnMainThread(final Runnable runnable) {
        if (mViewValidatedActionHandler == null) {
            mViewValidatedActionHandler = new Handler(Looper.getMainLooper());
        }
        mViewValidatedActionHandler.post(runnable);
    }

    private Field getLastField() {
        final Set<Field> fields = mRulesMap.keySet();

        Field lastField = null;
        for (Field field : fields) {
            lastField = field;
        }

        return lastField;
    }

    /**
     * Listener with callback methods that notifies the outcome of validation.
     *
     * @author Ragunath Jawahar {@literal <rj@mobsandgeeks.com>}
     * @since 1.0
     */
    public interface ValidationListener {

        /**
         * Called when all {@link Rule}s pass.
         */
        void onValidationSucceeded();

        /**
         * Called when one or several {@link Rule}s fail.
         *
         * @param errors List containing references to the {@link View}s and
         *               {@link Rule}s that failed.
         */
        void onValidationFailed(List<ValidationError> errors);
    }

    /**
     * Interface that provides a callback when all {@link Rule}s
     * associated with a {@link View} passes.
     *
     * @author Ragunath Jawahar {@literal <rj@mobsandgeeks.com>}
     * @since 2.0
     */
    public interface ViewValidatedAction {
        void onAllRulesPassed(Object object);
    }

    /**
     * Validation mode.
     *
     * @author Ragunath Jawahar {@literal <rj@mobsandgeeks.com>}
     * @since 2.0
     */
    public enum Mode {

        /**
         * BURST mode will validate all rules in all views before calling the
         * {@link Validator.ValidationListener#onValidationFailed(List)}
         * callback. Ordering and sequencing is optional.
         */
        BURST,

        /**
         * IMMEDIATE mode will stop the validation after validating all the rules
         * of the first failing view. Requires ordered rules, sequencing is optional.
         */
        IMMEDIATE
    }

    static class ValidationReport {
        List<ValidationError> errors;
        boolean hasMoreErrors;

        ValidationReport(final List<ValidationError> errors, final boolean hasMoreErrors) {
            this.errors = errors;
            this.hasMoreErrors = hasMoreErrors;
        }
    }

    private class AsyncValidationTask extends AsyncTask<Void, Void, ValidationReport> {
        private Field mField;
        private boolean mOrderedRules;
        private String mReasonSuffix;

        AsyncValidationTask(final Field field, final boolean orderedRules,
                            final String reasonSuffix) {
            this.mField = field;
            this.mOrderedRules = orderedRules;
            this.mReasonSuffix = reasonSuffix;
        }

        @Override
        protected ValidationReport doInBackground(final Void... params) {
            return validateTill(mField, mOrderedRules, mReasonSuffix);
        }

        @Override
        protected void onPostExecute(final ValidationReport validationReport) {
            triggerValidationListenerCallback(validationReport);
        }
    }

    static {
        // CheckBoxBooleanAdapter
        SARIPAAR_REGISTRY.register(CheckBox.class, Boolean.class,
                new CheckBoxBooleanAdapter(),
                AssertFalse.class, AssertTrue.class, Checked.class);

        // RadioGroupBooleanAdapter
        SARIPAAR_REGISTRY.register(RadioGroup.class, Boolean.class,
                new RadioGroupBooleanAdapter(),
                Checked.class);

        // RadioButtonBooleanAdapter
        SARIPAAR_REGISTRY.register(RadioButton.class, Boolean.class,
                new RadioButtonBooleanAdapter(),
                AssertFalse.class, AssertTrue.class, Checked.class);

        // SpinnerIndexAdapter
        SARIPAAR_REGISTRY.register(Spinner.class, Integer.class,
                new SpinnerIndexAdapter(),
                Select.class);

        // TextViewDoubleAdapter
        SARIPAAR_REGISTRY.register(DecimalMax.class, DecimalMin.class);

        // TextViewIntegerAdapter
        SARIPAAR_REGISTRY.register(Max.class, Min.class);

        // TextViewStringAdapter
        SARIPAAR_REGISTRY.register(
                ConfirmEmail.class, ConfirmPassword.class, CreditCard.class,
                Digits.class, Domain.class, Email.class, Future.class,
                IpAddress.class, Isbn.class, Length.class, NotEmpty.class,
                Password.class, Past.class, Pattern.class, Url.class);
    }
}
