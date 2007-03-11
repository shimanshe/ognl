// --------------------------------------------------------------------------
// Copyright (c) 1998-2004, Drew Davidson and Luke Blanshard
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
// Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
// Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// Neither the name of the Drew Davidson nor the names of its contributors
// may be used to endorse or promote products derived from this software
// without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
// FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
// COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
// BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
// OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
// AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
// THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
// --------------------------------------------------------------------------
package ognl;

import ognl.enhance.ExpressionCompiler;
import ognl.enhance.OgnlExpressionCompiler;
import ognl.enhance.UnsupportedCompilationException;

import java.beans.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.Permission;
import java.util.*;

/**
 * This is an abstract class with static methods that define runtime caching information in OGNL.
 *
 * @author Luke Blanshard (blanshlu@netscape.net)
 * @author Drew Davidson (drew@ognl.org)
 */
public class OgnlRuntime {

    public static final Object NotFound = new Object();
    public static final List NotFoundList = new ArrayList();
    public static final Map NotFoundMap = new HashMap();
    public static final Object[] NoArguments = new Object[]{};
    public static final Class[] NoArgumentTypes = new Class[]{};

    /**
     * Token returned by TypeConverter for no conversion possible
     */
    public static final Object NoConversionPossible = "ognl.NoConversionPossible";

    /**
     * Not an indexed property
     */
    public static int INDEXED_PROPERTY_NONE = 0;
    /**
     * JavaBeans IndexedProperty
     */
    public static int INDEXED_PROPERTY_INT = 1;
    /**
     * OGNL ObjectIndexedProperty
     */
    public static int INDEXED_PROPERTY_OBJECT = 2;

    public static final String NULL_STRING = "" + null;

    private static final String SET_PREFIX = "set";
    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";

    /**
     * Prefix padding for hexadecimal numbers to HEX_LENGTH.
     */
    private static final Map HEX_PADDING = new HashMap();

    private static final int HEX_LENGTH = 8;
    /**
     * Returned by <CODE>getUniqueDescriptor()</CODE> when the object is <CODE>null</CODE>.
     */
    private static final String NULL_OBJECT_STRING = "<null>";

    private static final ClassCache methodAccessors = new ClassCache();
    private static final ClassCache propertyAccessors = new ClassCache();
    private static final ClassCache elementsAccessors = new ClassCache();
    private static final ClassCache nullHandlers = new ClassCache();
    private static final ClassCache propertyDescriptorCache = new ClassCache();
    private static final ClassCache constructorCache = new ClassCache();
    private static final ClassCache staticMethodCache = new ClassCache();
    private static final ClassCache instanceMethodCache = new ClassCache();
    private static final ClassCache invokePermissionCache = new ClassCache();
    private static final ClassCache fieldCache = new ClassCache();
    private static final List superclasses = new ArrayList(); /* Used by fieldCache lookup */
    private static final ClassCache[] declaredMethods = new ClassCache[]{new ClassCache(), new ClassCache()};

    private static final Map primitiveTypes = new HashMap(101);
    private static final ClassCache primitiveDefaults = new ClassCache();
    private static final Map methodParameterTypesCache = new HashMap(101);
    private static final Map ctorParameterTypesCache = new HashMap(101);
    private static SecurityManager securityManager = System.getSecurityManager();
    private static final EvaluationPool evaluationPool = new EvaluationPool();
    private static final ObjectArrayPool objectArrayPool = new ObjectArrayPool();

    private static OgnlExpressionCompiler _compiler = new ExpressionCompiler();

    /**
     * This is a highly specialized map for storing values keyed by Class objects.
     */
    private static class ClassCache {

        /* this MUST be a power of 2 */
        private static final int TABLE_SIZE = 512;

        /* ...and now you see why. The table size is used as a mask for generating hashes */
        private static final int TABLE_SIZE_MASK = TABLE_SIZE - 1;

        private Entry[] table;

        private static class Entry {

            protected Entry next;
            protected Class key;
            protected Object value;

            public Entry(Class key, Object value)
            {
                super();
                this.key = key;
                this.value = value;
            }
        }

        public ClassCache()
        {
            super();
            this.table = new Entry[TABLE_SIZE];
        }

        public final Object get(Class key)
        {
            Object result = null;
            int i = key.hashCode() & TABLE_SIZE_MASK;

            for (Entry entry = table[i]; entry != null; entry = entry.next) {
                if (entry.key == key) {
                    result = entry.value;
                    break;
                }
            }
            return result;
        }

        public final Object put(Class key, Object value)
        {
            Object result = null;
            int i = key.hashCode() & TABLE_SIZE_MASK;
            Entry entry = table[i];

            if (entry == null) {
                table[i] = new Entry(key, value);
            } else {
                if (entry.key == key) {
                    result = entry.value;
                    entry.value = value;
                } else {
                    while (true) {
                        if (entry.key == key) {
                            /* replace value */
                            result = entry.value;
                            entry.value = value;
                            break;
                        } else {
                            if (entry.next == null) {
                                /* add value */
                                entry.next = new Entry(key, value);
                                break;
                            }
                        }
                        entry = entry.next;
                    }
                }
            }
            return result;
        }
    }

    private static IdentityHashMap PRIMITIVE_WRAPPER_CLASSES = new IdentityHashMap();

    static {
        PRIMITIVE_WRAPPER_CLASSES.put(Boolean.TYPE, Boolean.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Boolean.class, Boolean.TYPE);
        PRIMITIVE_WRAPPER_CLASSES.put(Byte.TYPE, Byte.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Byte.class, Byte.TYPE);
        PRIMITIVE_WRAPPER_CLASSES.put(Character.TYPE, Character.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Character.class, Character.TYPE);
        PRIMITIVE_WRAPPER_CLASSES.put(Short.TYPE, Short.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Short.class, Short.TYPE);
        PRIMITIVE_WRAPPER_CLASSES.put(Integer.TYPE, Integer.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Integer.class, Integer.TYPE);
        PRIMITIVE_WRAPPER_CLASSES.put(Long.TYPE, Long.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Long.class, Long.TYPE);
        PRIMITIVE_WRAPPER_CLASSES.put(Float.TYPE, Float.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Float.class, Float.TYPE);
        PRIMITIVE_WRAPPER_CLASSES.put(Double.TYPE, Double.class);
        PRIMITIVE_WRAPPER_CLASSES.put(Double.class, Double.TYPE);
    }

    private static final Map NUMERIC_CASTS = new HashMap();

    static {
        NUMERIC_CASTS.put(Double.class, "(double)");
        NUMERIC_CASTS.put(Float.class, "(float)");
        NUMERIC_CASTS.put(Integer.class, "(int)");
        NUMERIC_CASTS.put(Long.class, "(long)");
        NUMERIC_CASTS.put(BigDecimal.class, "(double)");
        NUMERIC_CASTS.put(BigInteger.class, "");
    }

    private static final Map NUMERIC_VALUES = new HashMap();

    static {
        NUMERIC_VALUES.put(Double.class, "doubleValue()");
        NUMERIC_VALUES.put(Float.class, "floatValue()");
        NUMERIC_VALUES.put(Integer.class, "intValue()");
        NUMERIC_VALUES.put(Long.class, "longValue()");
        NUMERIC_VALUES.put(Short.class, "shortValue()");
        NUMERIC_VALUES.put(Byte.class, "byteValue()");
        NUMERIC_VALUES.put(BigDecimal.class, "doubleValue()");
        NUMERIC_VALUES.put(BigInteger.class, "doubleValue()");
        NUMERIC_VALUES.put(Boolean.class, "booleanValue()");
    }

    private static final Map NUMERIC_LITERALS = new HashMap();

    static {
        NUMERIC_LITERALS.put(Integer.class, "");
        NUMERIC_LITERALS.put(Long.class, "l");
        NUMERIC_LITERALS.put(BigInteger.class, "d");
        NUMERIC_LITERALS.put(Float.class, "f");
        NUMERIC_LITERALS.put(Double.class, "d");
        NUMERIC_LITERALS.put(BigInteger.class, "d");
        NUMERIC_LITERALS.put(BigDecimal.class, "d");
    }

    private static final Map NUMERIC_DEFAULTS = new HashMap();

    static {
        NUMERIC_DEFAULTS.put(Boolean.class, Boolean.FALSE);
        NUMERIC_DEFAULTS.put(Byte.class, new Byte((byte) 0));
        NUMERIC_DEFAULTS.put(Short.class, new Short((short) 0));
        NUMERIC_DEFAULTS.put(Character.class, new Character((char) 0));
        NUMERIC_DEFAULTS.put(Integer.class, new Integer(0));
        NUMERIC_DEFAULTS.put(Long.class, new Long(0L));
        NUMERIC_DEFAULTS.put(Float.class, new Float(0.0f));
        NUMERIC_DEFAULTS.put(Double.class, new Double(0.0));

        NUMERIC_DEFAULTS.put(BigInteger.class, new BigInteger("0"));
        NUMERIC_DEFAULTS.put(BigDecimal.class, new BigDecimal(0.0));
    }

    static {
        PropertyAccessor p = new ArrayPropertyAccessor();
        setPropertyAccessor(Object.class, new ObjectPropertyAccessor());
        setPropertyAccessor(byte[].class, p);
        setPropertyAccessor(short[].class, p);
        setPropertyAccessor(char[].class, p);
        setPropertyAccessor(int[].class, p);
        setPropertyAccessor(long[].class, p);
        setPropertyAccessor(float[].class, p);
        setPropertyAccessor(double[].class, p);
        setPropertyAccessor(Object[].class, p);
        setPropertyAccessor(List.class, new ListPropertyAccessor());
        setPropertyAccessor(Map.class, new MapPropertyAccessor());
        setPropertyAccessor(Set.class, new SetPropertyAccessor());
        setPropertyAccessor(Iterator.class, new IteratorPropertyAccessor());
        setPropertyAccessor(Enumeration.class, new EnumerationPropertyAccessor());

        ElementsAccessor e = new ArrayElementsAccessor();
        setElementsAccessor(Object.class, new ObjectElementsAccessor());
        setElementsAccessor(byte[].class, e);
        setElementsAccessor(short[].class, e);
        setElementsAccessor(char[].class, e);
        setElementsAccessor(int[].class, e);
        setElementsAccessor(long[].class, e);
        setElementsAccessor(float[].class, e);
        setElementsAccessor(double[].class, e);
        setElementsAccessor(Object[].class, e);
        setElementsAccessor(Collection.class, new CollectionElementsAccessor());
        setElementsAccessor(Map.class, new MapElementsAccessor());
        setElementsAccessor(Iterator.class, new IteratorElementsAccessor());
        setElementsAccessor(Enumeration.class, new EnumerationElementsAccessor());
        setElementsAccessor(Number.class, new NumberElementsAccessor());

        NullHandler nh = new ObjectNullHandler();
        setNullHandler(Object.class, nh);
        setNullHandler(byte[].class, nh);
        setNullHandler(short[].class, nh);
        setNullHandler(char[].class, nh);
        setNullHandler(int[].class, nh);
        setNullHandler(long[].class, nh);
        setNullHandler(float[].class, nh);
        setNullHandler(double[].class, nh);
        setNullHandler(Object[].class, nh);

        MethodAccessor ma = new ObjectMethodAccessor();
        setMethodAccessor(Object.class, ma);
        setMethodAccessor(byte[].class, ma);
        setMethodAccessor(short[].class, ma);
        setMethodAccessor(char[].class, ma);
        setMethodAccessor(int[].class, ma);
        setMethodAccessor(long[].class, ma);
        setMethodAccessor(float[].class, ma);
        setMethodAccessor(double[].class, ma);
        setMethodAccessor(Object[].class, ma);

        primitiveTypes.put("boolean", Boolean.TYPE);
        primitiveTypes.put("byte", Byte.TYPE);
        primitiveTypes.put("short", Short.TYPE);
        primitiveTypes.put("char", Character.TYPE);
        primitiveTypes.put("int", Integer.TYPE);
        primitiveTypes.put("long", Long.TYPE);
        primitiveTypes.put("float", Float.TYPE);
        primitiveTypes.put("double", Double.TYPE);

        primitiveDefaults.put(Boolean.TYPE, Boolean.FALSE);
        primitiveDefaults.put(Boolean.class, Boolean.FALSE);
        primitiveDefaults.put(Byte.TYPE, new Byte((byte) 0));
        primitiveDefaults.put(Byte.class, new Byte((byte) 0));
        primitiveDefaults.put(Short.TYPE, new Short((short) 0));
        primitiveDefaults.put(Short.class, new Short((short) 0));
        primitiveDefaults.put(Character.TYPE, new Character((char) 0));
        primitiveDefaults.put(Integer.TYPE, new Integer(0));
        primitiveDefaults.put(Long.TYPE, new Long(0L));
        primitiveDefaults.put(Float.TYPE, new Float(0.0f));
        primitiveDefaults.put(Double.TYPE, new Double(0.0));

        primitiveDefaults.put(BigInteger.class, new BigInteger("0"));
        primitiveDefaults.put(BigDecimal.class, new BigDecimal(0.0));
    }

    public static String getNumericValueGetter(Class type)
    {
        return (String) NUMERIC_VALUES.get(type);
    }

    public static Class getPrimitiveWrapperClass(Class primitiveClass)
    {
        return (Class) PRIMITIVE_WRAPPER_CLASSES.get(primitiveClass);
    }

    public static String getNumericCast(Class type)
    {
        return (String) NUMERIC_CASTS.get(type);
    }

    public static String getNumericLiteral(Class type)
    {
        return (String) NUMERIC_LITERALS.get(type);
    }

    public static void setCompiler(OgnlExpressionCompiler compiler)
    {
        _compiler = compiler;
    }

    public static OgnlExpressionCompiler getCompiler()
    {
        return _compiler;
    }

    public static void compileExpression(OgnlContext context, Node expression, Object root)
            throws Exception
    {
        _compiler.compileExpression(context, expression, root);
    }

    /**
     * Gets the "target" class of an object for looking up accessors that are registered on the
     * target. If the object is a Class object this will return the Class itself, else it will
     * return object's getClass() result.
     */
    public static Class getTargetClass(Object o)
    {
        return (o == null) ? null : ((o instanceof Class) ? (Class) o : o.getClass());
    }

    /**
     * Returns the base name (the class name without the package name prepended) of the object
     * given.
     */
    public static String getBaseName(Object o)
    {
        return (o == null) ? null : getClassBaseName(o.getClass());
    }

    /**
     * Returns the base name (the class name without the package name prepended) of the class given.
     */
    public static String getClassBaseName(Class c)
    {
        String s = c.getName();

        return s.substring(s.lastIndexOf('.') + 1);
    }

    public static String getClassName(Object o, boolean fullyQualified)
    {
        if (!(o instanceof Class)) {
            o = o.getClass();
        }
        return getClassName((Class) o, fullyQualified);
    }

    public static String getClassName(Class c, boolean fullyQualified)
    {
        return fullyQualified ? c.getName() : getClassBaseName(c);
    }

    /**
     * Returns the package name of the object's class.
     */
    public static String getPackageName(Object o)
    {
        return (o == null) ? null : getClassPackageName(o.getClass());
    }

    /**
     * Returns the package name of the class given.
     */
    public static String getClassPackageName(Class c)
    {
        String s = c.getName();
        int i = s.lastIndexOf('.');

        return (i < 0) ? null : s.substring(0, i);
    }

    /**
     * Returns a "pointer" string in the usual format for these things - 0x<hex digits>.
     */
    public static String getPointerString(int num)
    {
        StringBuffer result = new StringBuffer();
        String hex = Integer.toHexString(num), pad;
        Integer l = new Integer(hex.length());

        // result.append(HEX_PREFIX);
        if ((pad = (String) HEX_PADDING.get(l)) == null) {
            StringBuffer pb = new StringBuffer();

            for (int i = hex.length(); i < HEX_LENGTH; i++) {
                pb.append('0');
            }
            pad = new String(pb);
            HEX_PADDING.put(l, pad);
        }
        result.append(pad);
        result.append(hex);
        return new String(result);
    }

    /**
     * Returns a "pointer" string in the usual format for these things - 0x<hex digits> for the
     * object given. This will always return a unique value for each object.
     */
    public static String getPointerString(Object o)
    {
        return getPointerString((o == null) ? 0 : System.identityHashCode(o));
    }

    /**
     * Returns a unique descriptor string that includes the object's class and a unique integer
     * identifier. If fullyQualified is true then the class name will be fully qualified to include
     * the package name, else it will be just the class' base name.
     */
    public static String getUniqueDescriptor(Object object, boolean fullyQualified)
    {
        StringBuffer result = new StringBuffer();

        if (object != null) {
            if (object instanceof Proxy) {
                Class interfaceClass = object.getClass().getInterfaces()[0];

                result.append(getClassName(interfaceClass, fullyQualified));
                result.append('^');
                object = Proxy.getInvocationHandler(object);
            }
            result.append(getClassName(object, fullyQualified));
            result.append('@');
            result.append(getPointerString(object));
        } else {
            result.append(NULL_OBJECT_STRING);
        }
        return new String(result);
    }

    /**
     * Returns a unique descriptor string that includes the object's class' base name and a unique
     * integer identifier.
     */
    public static String getUniqueDescriptor(Object object)
    {
        return getUniqueDescriptor(object, false);
    }

    /**
     * Utility to convert a List into an Object[] array. If the list is zero elements this will
     * return a constant array; toArray() on List always returns a new object and this is wasteful
     * for our purposes.
     */
    public static Object[] toArray(List list)
    {
        Object[] result;
        int size = list.size();

        if (size == 0) {
            result = NoArguments;
        } else {
            result = getObjectArrayPool().create(list.size());
            for (int i = 0; i < size; i++) {
                result[i] = list.get(i);
            }
        }
        return result;
    }

    /**
     * Returns the parameter types of the given method.
     */
    public static Class[] getParameterTypes(Method m)
    {
        synchronized (methodParameterTypesCache) {
            Class[] result;

            if ((result = (Class[]) methodParameterTypesCache.get(m)) == null) {
                methodParameterTypesCache.put(m, result = m.getParameterTypes());
            }
            return result;
        }
    }

    /**
     * Returns the parameter types of the given method.
     */
    public static Class[] getParameterTypes(Constructor c)
    {
        synchronized (ctorParameterTypesCache) {
            Class[] result;

            if ((result = (Class[]) ctorParameterTypesCache.get(c)) == null) {
                ctorParameterTypesCache.put(c, result = c.getParameterTypes());
            }
            return result;
        }
    }

    /**
     * Gets the SecurityManager that OGNL uses to determine permissions for invoking methods.
     *
     * @return SecurityManager for OGNL
     */
    public static SecurityManager getSecurityManager()
    {
        return securityManager;
    }

    /**
     * Sets the SecurityManager that OGNL uses to determine permissions for invoking methods.
     *
     * @param value SecurityManager to set
     */
    public static void setSecurityManager(SecurityManager value)
    {
        securityManager = value;
    }

    /**
     * Permission will be named "invoke.<declaring-class>.<method-name>".
     */
    public static Permission getPermission(Method method)
    {
        Permission result = null;
        Class mc = method.getDeclaringClass();

        synchronized (invokePermissionCache) {
            Map permissions = (Map) invokePermissionCache.get(mc);

            if (permissions == null) {
                invokePermissionCache.put(mc, permissions = new HashMap(101));
            }
            if ((result = (Permission) permissions.get(method.getName())) == null) {
                result = new OgnlInvokePermission("invoke." + mc.getName() + "." + method.getName());
                permissions.put(method.getName(), result);
            }
        }
        return result;
    }

    public static Object invokeMethod(Object target, Method method, Object[] argsArray)
            throws InvocationTargetException, IllegalAccessException
    {
        Object result;
        boolean wasAccessible = true;

        if (securityManager != null) {
            try {
                securityManager.checkPermission(getPermission(method));
            } catch (SecurityException ex) {
                throw new IllegalAccessException("Method [" + method + "] cannot be accessed.");
            }
        }
        if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            if (!(wasAccessible = ((AccessibleObject) method).isAccessible())) {
                ((AccessibleObject) method).setAccessible(true);
            }
        }
        result = method.invoke(target, argsArray);
        if (!wasAccessible) {
            ((AccessibleObject) method).setAccessible(false);
        }
        return result;
    }

    /**
     * Gets the class for a method argument that is appropriate for looking up methods by
     * reflection, by looking for the standard primitive wrapper classes and exchanging for them
     * their underlying primitive class objects. Other classes are passed through unchanged.
     *
     * @param arg an object that is being passed to a method
     * @return the class to use to look up the method
     */
    public static final Class getArgClass(Object arg)
    {
        if (arg == null)
            return null;
        Class c = arg.getClass();
        if (c == Boolean.class)
            return Boolean.TYPE;
        else if (c.getSuperclass() == Number.class) {
            if (c == Integer.class)
                return Integer.TYPE;
            if (c == Double.class)
                return Double.TYPE;
            if (c == Byte.class)
                return Byte.TYPE;
            if (c == Long.class)
                return Long.TYPE;
            if (c == Float.class)
                return Float.TYPE;
            if (c == Short.class)
                return Short.TYPE;
        } else if (c == Character.class)
            return Character.TYPE;
        return c;
    }

    /**
     * Tells whether the given object is compatible with the given class ---that is, whether the
     * given object can be passed as an argument to a method or constructor whose parameter type is
     * the given class. If object is null this will return true because null is compatible with any
     * type.
     */
    public static final boolean isTypeCompatible(Object object, Class c)
    {
        boolean result = true;

        if (object != null) {
            if (c.isPrimitive()) {
                if (getArgClass(object) != c) {
                    result = false;
                }
            } else if (!c.isInstance(object)) {
                result = false;
            }
        }
        return result;
    }

    /**
     * Tells whether the given array of objects is compatible with the given array of classes---that
     * is, whether the given array of objects can be passed as arguments to a method or constructor
     * whose parameter types are the given array of classes.
     */
    public static final boolean areArgsCompatible(Object[] args, Class[] classes)
    {
        boolean result = true;

        if (args.length != classes.length) {
            result = false;
        } else {
            for (int index = 0, count = args.length; result && (index < count); ++index) {
                result = isTypeCompatible(args[index], classes[index]);
            }
        }
        return result;
    }

    /**
     * Tells whether the first array of classes is more specific than the second. Assumes that the
     * two arrays are of the same length.
     */
    public static final boolean isMoreSpecific(Class[] classes1, Class[] classes2)
    {
        for (int index = 0, count = classes1.length; index < count; ++index) {
            Class c1 = classes1[index], c2 = classes2[index];
            if (c1 == c2)
                continue;
            else if (c1.isPrimitive())
                return true;
            else if (c1.isAssignableFrom(c2))
                return false;
            else if (c2.isAssignableFrom(c1))
                return true;
        }

        // They are the same! So the first is not more specific than the second.
        return false;
    }

    public static String getModifierString(int modifiers)
    {
        String result;

        if (Modifier.isPublic(modifiers))
            result = "public";
        else if (Modifier.isProtected(modifiers))
            result = "protected";
        else if (Modifier.isPrivate(modifiers))
            result = "private";
        else
            result = "";
        if (Modifier.isStatic(modifiers))
            result = "static " + result;
        if (Modifier.isFinal(modifiers))
            result = "final " + result;
        if (Modifier.isNative(modifiers))
            result = "native " + result;
        if (Modifier.isSynchronized(modifiers))
            result = "synchronized " + result;
        if (Modifier.isTransient(modifiers))
            result = "transient " + result;
        return result;
    }

    public static Class classForName(OgnlContext context, String className)
            throws ClassNotFoundException
    {
        Class result = (Class) primitiveTypes.get(className);

        if (result == null) {
            ClassResolver resolver;

            if ((context == null) || ((resolver = context.getClassResolver()) == null)) {
                resolver = OgnlContext.DEFAULT_CLASS_RESOLVER;
            }
            result = resolver.classForName(className, context);
        }
        return result;
    }

    public static boolean isInstance(OgnlContext context, Object value, String className)
            throws OgnlException
    {
        try {
            Class c = classForName(context, className);
            return c.isInstance(value);
        } catch (ClassNotFoundException e) {
            throw new OgnlException("No such class: " + className, e);
        }
    }

    public static Object getPrimitiveDefaultValue(Class forClass)
    {
        return primitiveDefaults.get(forClass);
    }

    public static Object getNumericDefaultValue(Class forClass)
    {
        return NUMERIC_DEFAULTS.get(forClass);
    }

    public static Object getConvertedType(OgnlContext context, Object target, Member member, String propertyName,
                                          Object value, Class type)
    {
        return context.getTypeConverter().convertValue(context, target, member, propertyName, value, type);
    }

    public static boolean getConvertedTypes(OgnlContext context, Object target, Member member, String propertyName,
                                            Class[] parameterTypes, Object[] args, Object[] newArgs)
    {
        boolean result = false;

        if (parameterTypes.length == args.length) {
            result = true;
            for (int i = 0, ilast = parameterTypes.length - 1; result && (i <= ilast); i++) {
                Object arg = args[i];
                Class type = parameterTypes[i];

                if (isTypeCompatible(arg, type)) {
                    newArgs[i] = arg;
                } else {
                    Object v = getConvertedType(context, target, member, propertyName, arg, type);

                    if (v == OgnlRuntime.NoConversionPossible) {
                        result = false;
                    } else {
                        newArgs[i] = v;
                    }
                }
            }
        }
        return result;
    }

    public static Method getConvertedMethodAndArgs(OgnlContext context, Object target, String propertyName,
                                                   List methods, Object[] args, Object[] newArgs)
    {
        Method result = null;
        TypeConverter converter = context.getTypeConverter();

        if ((converter != null) && (methods != null)) {
            for (int i = 0, icount = methods.size(); (result == null) && (i < icount); i++) {
                Method m = (Method) methods.get(i);
                Class[] parameterTypes = getParameterTypes(m);

                if (getConvertedTypes(context, target, m, propertyName, parameterTypes, args, newArgs)) {
                    result = m;
                }
            }
        }
        return result;
    }

    public static Constructor getConvertedConstructorAndArgs(OgnlContext context, Object target, List constructors,
                                                             Object[] args, Object[] newArgs)
    {
        Constructor result = null;
        TypeConverter converter = context.getTypeConverter();

        if ((converter != null) && (constructors != null)) {
            for (int i = 0, icount = constructors.size(); (result == null) && (i < icount); i++) {
                Constructor ctor = (Constructor) constructors.get(i);
                Class[] parameterTypes = getParameterTypes(ctor);

                if (getConvertedTypes(context, target, ctor, null, parameterTypes, args, newArgs)) {
                    result = ctor;
                }
            }
        }
        return result;
    }

    /**
     * Gets the appropriate method to be called for the given target, method name and arguments. If
     * successful this method will return the Method within the target that can be called and the
     * converted arguments in actualArgs. If unsuccessful this method will return null and the
     * actualArgs will be empty.
     */
    public static Method getAppropriateMethod(OgnlContext context, Object source, Object target, String methodName,
                                              String propertyName, List methods, Object[] args, Object[] actualArgs)
    {
        Method result = null;
        Class[] resultParameterTypes = null;

        if (methods != null) {
            for (int i = 0, icount = methods.size(); i < icount; i++) {
                Method m = (Method) methods.get(i);
                Class[] mParameterTypes = getParameterTypes(m);

                if (areArgsCompatible(args, mParameterTypes)
                        && ((result == null) || isMoreSpecific(mParameterTypes, resultParameterTypes))) {
                    result = m;
                    resultParameterTypes = mParameterTypes;
                    System.arraycopy(args, 0, actualArgs, 0, args.length);
                    for (int j = 0; j < mParameterTypes.length; j++) {
                        Class type = mParameterTypes[j];

                        if (type.isPrimitive() && (actualArgs[j] == null)) {
                            actualArgs[j] = getConvertedType(context, source, result, propertyName, null, type);
                        }
                    }
                }
            }
        }
        if (result == null) {
            result = getConvertedMethodAndArgs(context, target, propertyName, methods, args, actualArgs);
        }
        return result;
    }

    public static Object callAppropriateMethod(OgnlContext context, Object source, Object target, String methodName,
                                               String propertyName, List methods, Object[] args)
            throws MethodFailedException
    {
        Throwable reason = null;
        Object[] actualArgs = objectArrayPool.create(args.length);

        try {
            Method method = getAppropriateMethod(context, source, target, methodName, propertyName, methods, args,
                                                 actualArgs);

            if ((method == null) || !isMethodAccessible(context, source, method, propertyName)) {
                StringBuffer buffer = new StringBuffer();

                for (int i = 0, ilast = args.length - 1; i <= ilast; i++) {
                    Object arg = args[i];

                    buffer.append((arg == null) ? NULL_STRING : arg.getClass().getName());
                    if (i < ilast) {
                        buffer.append(", ");
                    }
                }

                throw new NoSuchMethodException(methodName + "(" + buffer + ")");
            }
            return invokeMethod(target, method, actualArgs);
        } catch (NoSuchMethodException e) {
            reason = e;
        } catch (IllegalAccessException e) {
            reason = e;
        } catch (InvocationTargetException e) {
            reason = e.getTargetException();
        } finally {
            objectArrayPool.recycle(actualArgs);
        }
        throw new MethodFailedException(source, methodName, reason);
    }

    public static final Object callStaticMethod(OgnlContext context, String className, String methodName, Object[] args)
            throws OgnlException, MethodFailedException
    {
        try {
            Class targetClass = classForName(context, className);
            MethodAccessor ma = getMethodAccessor(targetClass);

            return ma.callStaticMethod(context, targetClass, methodName, args);
        } catch (ClassNotFoundException ex) {
            throw new MethodFailedException(className, methodName, ex);
        }
    }

    public static final Object callMethod(OgnlContext context, Object target, String methodName, String propertyName,
                                          Object[] args)
            throws OgnlException, MethodFailedException
    {
        Object result;

        if (target != null) {
            MethodAccessor ma = getMethodAccessor(target.getClass());

            result = ma.callMethod(context, target, methodName, args);
        } else {
            throw new NullPointerException("target is null for method " + methodName);
        }
        return result;
    }

    public static final Object callConstructor(OgnlContext context, String className, Object[] args)
            throws OgnlException
    {
        Throwable reason = null;
        Object[] actualArgs = args;

        try {
            Constructor ctor = null;
            Class[] ctorParameterTypes = null;
            Class target = classForName(context, className);
            List constructors = getConstructors(target);

            for (int i = 0, icount = constructors.size(); i < icount; i++) {
                Constructor c = (Constructor) constructors.get(i);
                Class[] cParameterTypes = getParameterTypes(c);

                if (areArgsCompatible(args, cParameterTypes)
                        && (ctor == null || isMoreSpecific(cParameterTypes, ctorParameterTypes))) {
                    ctor = c;
                    ctorParameterTypes = cParameterTypes;
                }
            }
            if (ctor == null) {
                actualArgs = objectArrayPool.create(args.length);
                if ((ctor = getConvertedConstructorAndArgs(context, target, constructors, args, actualArgs)) == null) {
                    throw new NoSuchMethodException();
                }
            }
            if (!context.getMemberAccess().isAccessible(context, target, ctor, null)) {
                throw new IllegalAccessException(
                        "access denied to " + target.getName() + "()");
            }
            return ctor.newInstance(actualArgs);
        } catch (ClassNotFoundException e) {
            reason = e;
        } catch (NoSuchMethodException e) {
            reason = e;
        } catch (IllegalAccessException e) {
            reason = e;
        } catch (InvocationTargetException e) {
            reason = e.getTargetException();
        } catch (InstantiationException e) {
            reason = e;
        } finally {
            if (actualArgs != args) {
                objectArrayPool.recycle(actualArgs);
            }
        }

        throw new MethodFailedException(className, "new", reason);
    }

    public static final Object getMethodValue(OgnlContext context, Object target, String propertyName)
            throws OgnlException, IllegalAccessException, NoSuchMethodException, IntrospectionException
    {
        return getMethodValue(context, target, propertyName, false);
    }

    /**
     * If the checkAccessAndExistence flag is true this method will check to see if the method
     * exists and if it is accessible according to the context's MemberAccess. If neither test
     * passes this will return NotFound.
     */
    public static final Object getMethodValue(OgnlContext context, Object target, String propertyName,
                                              boolean checkAccessAndExistence)
            throws OgnlException, IllegalAccessException, NoSuchMethodException, IntrospectionException
    {
        Object result = null;
        Method m = getGetMethod(context, (target == null) ? null : target.getClass(), propertyName);

        if (checkAccessAndExistence) {
            if ((m == null) || !context.getMemberAccess().isAccessible(context, target, m, propertyName)) {
                result = NotFound;
            }
        }
        if (result == null) {
            if (m != null) {
                try {
                    result = invokeMethod(target, m, NoArguments);
                } catch (InvocationTargetException ex) {
                    throw new OgnlException(propertyName, ex.getTargetException());
                }
            } else {
                throw new NoSuchMethodException(propertyName);
            }
        }
        return result;
    }

    public static final boolean setMethodValue(OgnlContext context, Object target, String propertyName, Object value)
            throws OgnlException, IllegalAccessException, NoSuchMethodException, MethodFailedException,
            IntrospectionException
    {
        return setMethodValue(context, target, propertyName, value, false);
    }

    public static final boolean setMethodValue(OgnlContext context, Object target, String propertyName, Object value,
                                               boolean checkAccessAndExistence)
            throws OgnlException, IllegalAccessException, NoSuchMethodException, MethodFailedException,
            IntrospectionException
    {
        boolean result = true;
        Method m = getSetMethod(context, (target == null) ? null : target.getClass(), propertyName);

        if (checkAccessAndExistence) {
            if ((m == null) || !context.getMemberAccess().isAccessible(context, target, m, propertyName)) {
                result = false;
            }
        }
        if (result) {
            if (m != null) {
                Object[] args = objectArrayPool.create(value);

                try {
                    callAppropriateMethod(context, target, target, m.getName(), propertyName,
                                          Collections.nCopies(1, m), args);
                } finally {
                    objectArrayPool.recycle(args);
                }
            } else {
                result = false;
            }
        }
        return result;
    }

    public static final List getConstructors(Class targetClass)
    {
        List result;

        synchronized (constructorCache) {
            if ((result = (List) constructorCache.get(targetClass)) == null) {
                constructorCache.put(targetClass, result = Arrays.asList(targetClass.getConstructors()));
            }
        }
        return result;
    }

    public static Map getMethods(Class targetClass, boolean staticMethods)
    {
        ClassCache cache = (staticMethods ? staticMethodCache : instanceMethodCache);
        Map result;

        synchronized (cache) {
            if ((result = (Map) cache.get(targetClass)) == null) {
                cache.put(targetClass, result = new HashMap(23));
                for (Class c = targetClass; c != null; c = c.getSuperclass()) {
                    Method[] ma = c.getDeclaredMethods();

                    for (int i = 0, icount = ma.length; i < icount; i++) {
                        if (Modifier.isStatic(ma[i].getModifiers()) == staticMethods) {
                            List ml = (List) result.get(ma[i].getName());

                            if (ml == null)
                                result.put(ma[i].getName(), ml = new ArrayList());
                            ml.add(ma[i]);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static List getMethods(Class targetClass, String name, boolean staticMethods)
    {
        return (List) getMethods(targetClass, staticMethods).get(name);
    }

    public static Map getFields(Class targetClass)
    {
        Map result;

        synchronized (fieldCache) {
            if ((result = (Map) fieldCache.get(targetClass)) == null) {
                Field fa[];

                result = new HashMap(23);
                fa = targetClass.getDeclaredFields();
                for (int i = 0; i < fa.length; i++) {
                    result.put(fa[i].getName(), fa[i]);
                }
                fieldCache.put(targetClass, result);
            }
        }
        return result;
    }

    public static Field getField(Class inClass, String name)
    {
        Field result = null;

        synchronized (fieldCache) {
            Object o = getFields(inClass).get(name);

            if (o == null) {
                superclasses.clear();
                for (Class sc = inClass; (sc != null); sc = sc.getSuperclass()) {
                    if ((o = getFields(sc).get(name)) == NotFound)
                        break;
                    
                    superclasses.add(sc);
                    
                    if ((result = (Field) o) != null)
                        break;
                }
                /*
                 * Bubble the found value (either cache miss or actual field) to all supeclasses
                 * that we saw for quicker access next time.
                 */
                for (int i = 0, icount = superclasses.size(); i < icount; i++) {
                    getFields((Class) superclasses.get(i)).put(name, (result == null) ? NotFound : result);
                }
            } else {
                if (o instanceof Field) {
                    result = (Field) o;
                } else {
                    if (result == NotFound)
                        result = null;
                }
            }
        }
        return result;
    }

    public static Object getFieldValue(OgnlContext context, Object target, String propertyName)
            throws NoSuchFieldException
    {
        return getFieldValue(context, target, propertyName, false);
    }

    public static Object getFieldValue(OgnlContext context, Object target, String propertyName,
                                             boolean checkAccessAndExistence)
            throws NoSuchFieldException
    {
        Object result = null;
        Field f = getField((target == null) ? null : target.getClass(), propertyName);

        if (checkAccessAndExistence) {
            if ((f == null) || !context.getMemberAccess().isAccessible(context, target, f, propertyName)) {
                result = NotFound;
            }
        }
        if (result == null) {
            if (f == null) {
                throw new NoSuchFieldException(propertyName);
            } else {
                try {
                    Object state = null;

                    if (!Modifier.isStatic(f.getModifiers())) {
                        state = context.getMemberAccess().setup(context, target, f, propertyName);
                        result = f.get(target);
                        context.getMemberAccess().restore(context, target, f, propertyName, state);
                    } else
                        throw new NoSuchFieldException(propertyName);
                    
                } catch (IllegalAccessException ex) {
                    throw new NoSuchFieldException(propertyName);
                }
            }
        }
        return result;
    }

    public static boolean setFieldValue(OgnlContext context, Object target, String propertyName, Object value)
            throws OgnlException
    {
        boolean result = false;

        try {
            Field f = getField((target == null) ? null : target.getClass(), propertyName);
            Object state;

            if ((f != null) && !Modifier.isStatic(f.getModifiers())) {
                state = context.getMemberAccess().setup(context, target, f, propertyName);
                try {
                    if (isTypeCompatible(value, f.getType())
                            || ((value = getConvertedType(context, target, f, propertyName, value, f.getType())) != null)) {
                        f.set(target, value);
                        result = true;
                    }
                } finally {
                    context.getMemberAccess().restore(context, target, f, propertyName, state);
                }
            }
        } catch (IllegalAccessException ex) {
            throw new NoSuchPropertyException(target, propertyName, ex);
        }
        return result;
    }

    public static boolean isFieldAccessible(OgnlContext context, Object target, Class inClass, String propertyName)
    {
        return isFieldAccessible(context, target, getField(inClass, propertyName), propertyName);
    }

    public static boolean isFieldAccessible(OgnlContext context, Object target, Field field, String propertyName)
    {
        return context.getMemberAccess().isAccessible(context, target, field, propertyName);
    }

    public static boolean hasField(OgnlContext context, Object target, Class inClass, String propertyName)
    {
        Field f = getField(inClass, propertyName);

        return (f != null) && isFieldAccessible(context, target, f, propertyName);
    }

    public static Object getStaticField(OgnlContext context, String className, String fieldName)
            throws OgnlException
    {
        Exception reason = null;
        try {
            Class c = classForName(context, className);

            /*
             * Check for virtual static field "class"; this cannot interfere with normal static
             * fields because it is a reserved word.
             */
            if (fieldName.equals("class")) {
                return c;
            } else {
                Field f = c.getField(fieldName);
                if (!Modifier.isStatic(f.getModifiers()))
                    throw new OgnlException("Field " + fieldName + " of class " + className + " is not static");
                return f.get(null);
            }
        } catch (ClassNotFoundException e) {
            reason = e;
        } catch (NoSuchFieldException e) {
            reason = e;
        } catch (SecurityException e) {
            reason = e;
        } catch (IllegalAccessException e) {
            reason = e;
        }

        throw new OgnlException("Could not get static field " + fieldName + " from class " + className, reason);
    }

    public static List getDeclaredMethods(Class targetClass, String propertyName, boolean findSets)
    {
        List result = null;
        ClassCache cache = declaredMethods[findSets ? 0 : 1];

        synchronized (cache) {
            Map propertyCache = (Map) cache.get(targetClass);

            if ((propertyCache == null) || ((result = (List) propertyCache.get(propertyName)) == null)) {

                String baseName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

                for (Class c = targetClass; c != null; c = c.getSuperclass()) {
                    Method[] methods = c.getDeclaredMethods();

                    for (int i = 0; i < methods.length; i++) {
                        String ms = methods[i].getName();

                        if (ms.endsWith(baseName)) {
                            boolean isSet = false, isIs = false;

                            if ((isSet = ms.startsWith(SET_PREFIX)) || ms.startsWith(GET_PREFIX)
                                    || (isIs = ms.startsWith(IS_PREFIX))) {
                                int prefixLength = (isIs ? 2 : 3);

                                if (isSet == findSets) {
                                    if (baseName.length() == (ms.length() - prefixLength)) {
                                        if (result == null) {
                                            result = new ArrayList();
                                        }
                                        result.add(methods[i]);
                                    }
                                }
                            }
                        }
                    }
                }
                if (propertyCache == null) {
                    cache.put(targetClass, propertyCache = new HashMap(101));
                }
                
                propertyCache.put(propertyName, (result == null) ? NotFoundList : result);
            }
            return (result == NotFoundList) ? null : result;
        }
    }

    public static Method getGetMethod(OgnlContext context, Class targetClass, String propertyName)
            throws IntrospectionException, OgnlException
    {
        Method result = null;
        PropertyDescriptor pd = getPropertyDescriptor(targetClass, propertyName);

        if (pd == null) {
            List methods = getDeclaredMethods(targetClass, propertyName, false /*
                                                                                 * find 'get'
                                                                                 * methods
                                                                                 */);

            if (methods != null) {
                for (int i = 0, icount = methods.size(); i < icount; i++) {
                    Method m = (Method) methods.get(i);
                    Class[] mParameterTypes = getParameterTypes(m);

                    if (mParameterTypes.length == 0) {
                        result = m;
                        break;
                    }
                }
            }
        } else {
            result = pd.getReadMethod();
        }
        return result;
    }

    public static boolean isMethodAccessible(OgnlContext context, Object target, Method method,
                                                   String propertyName)
    {
        return (method != null) && context.getMemberAccess().isAccessible(context, target, method, propertyName);
    }

    public static boolean hasGetMethod(OgnlContext context, Object target, Class targetClass, String propertyName)
            throws IntrospectionException, OgnlException
    {
        return isMethodAccessible(context, target, getGetMethod(context, targetClass, propertyName), propertyName);
    }

    public static Method getSetMethod(OgnlContext context, Class targetClass, String propertyName)
            throws IntrospectionException, OgnlException
    {
        Method result = null;
        PropertyDescriptor pd = getPropertyDescriptor(targetClass, propertyName);

        if (pd == null) {
            List methods = getDeclaredMethods(targetClass, propertyName, true /* find 'set' methods */);
            
            if (methods != null) {
                for (int i = 0, icount = methods.size(); i < icount; i++) {
                    Method m = (Method) methods.get(i);
                    Class[] mParameterTypes = getParameterTypes(m);

                    if (mParameterTypes.length == 1) {
                        result = m;
                        break;
                    }
                }
            }
        } else {
            result = pd.getWriteMethod();
        }
        return result;
    }

    public static final boolean hasSetMethod(OgnlContext context, Object target, Class targetClass, String propertyName)
            throws IntrospectionException, OgnlException
    {
        return isMethodAccessible(context, target, getSetMethod(context, targetClass, propertyName), propertyName);
    }

    public static final boolean hasGetProperty(OgnlContext context, Object target, Object oname)
            throws IntrospectionException, OgnlException
    {
        Class targetClass = (target == null) ? null : target.getClass();
        String name = oname.toString();

        return hasGetMethod(context, target, targetClass, name) || hasField(context, target, targetClass, name);
    }

    public static final boolean hasSetProperty(OgnlContext context, Object target, Object oname)
            throws IntrospectionException, OgnlException
    {
        Class targetClass = (target == null) ? null : target.getClass();
        String name = oname.toString();

        return hasSetMethod(context, target, targetClass, name) || hasField(context, target, targetClass, name);
    }

    private static final boolean indexMethodCheck(List methods)
    {
        boolean result = false;

        if (methods.size() > 0) {
            Method fm = (Method) methods.get(0);
            Class[] fmpt = getParameterTypes(fm);
            int fmpc = fmpt.length;
            Class lastMethodClass = fm.getDeclaringClass();

            result = true;
            for (int i = 1; result && (i < methods.size()); i++) {
                Method m = (Method) methods.get(i);
                Class c = m.getDeclaringClass();

                // Check to see if more than one method implemented per class
                if (lastMethodClass == c) {
                    result = false;
                } else {
                    Class[] mpt = getParameterTypes(fm);
                    int mpc = fmpt.length;

                    if (fmpc != mpc) {
                        result = false;
                    }
                    for (int j = 0; j < fmpc; j++) {
                        if (fmpt[j] != mpt[j]) {
                            result = false;
                            break;
                        }
                    }
                }
                lastMethodClass = c;
            }
        }
        return result;
    }

    private static final void findObjectIndexedPropertyDescriptors(Class targetClass, Map intoMap)
            throws OgnlException
    {
        Map allMethods = getMethods(targetClass, false);
        Map pairs = new HashMap(101);

        for (Iterator it = allMethods.keySet().iterator(); it.hasNext();) {
            String methodName = (String) it.next();
            List methods = (List) allMethods.get(methodName);

            /*
             * Only process set/get where there is exactly one implementation of the method per
             * class and those implementations are all the same
             */
            if (indexMethodCheck(methods)) {
                boolean isGet = false, isSet = false;
                Method m = (Method) methods.get(0);

                if (((isSet = methodName.startsWith(SET_PREFIX)) || (isGet = methodName.startsWith(GET_PREFIX)))
                        && (methodName.length() > 3)) {
                    String propertyName = Introspector.decapitalize(methodName.substring(3));
                    Class[] parameterTypes = getParameterTypes(m);
                    int parameterCount = parameterTypes.length;

                    if (isGet && (parameterCount == 1) && (m.getReturnType() != Void.TYPE)) {
                        List pair = (List) pairs.get(propertyName);

                        if (pair == null) {
                            pairs.put(propertyName, pair = new ArrayList());
                        }
                        pair.add(m);
                    }
                    if (isSet && (parameterCount == 2) && (m.getReturnType() == Void.TYPE)) {
                        List pair = (List) pairs.get(propertyName);

                        if (pair == null) {
                            pairs.put(propertyName, pair = new ArrayList());
                        }
                        pair.add(m);
                    }
                }
            }
        }
        for (Iterator it = pairs.keySet().iterator(); it.hasNext();) {
            String propertyName = (String) it.next();
            List methods = (List) pairs.get(propertyName);

            if (methods.size() == 2) {
                Method method1 = (Method) methods.get(0), method2 = (Method) methods.get(1), setMethod = (method1
                        .getParameterTypes().length == 2) ? method1 : method2, getMethod = (setMethod == method1) ? method2
                                                                                           : method1;
                Class keyType = getMethod.getParameterTypes()[0], propertyType = getMethod.getReturnType();

                if (keyType == setMethod.getParameterTypes()[0]) {
                    if (propertyType == setMethod.getParameterTypes()[1]) {
                        ObjectIndexedPropertyDescriptor propertyDescriptor;

                        try {
                            propertyDescriptor = new ObjectIndexedPropertyDescriptor(propertyName, propertyType,
                                                                                     getMethod, setMethod);
                        } catch (Exception ex) {
                            throw new OgnlException("creating object indexed property descriptor for '" + propertyName
                                    + "' in " + targetClass, ex);
                        }
                        intoMap.put(propertyName, propertyDescriptor);
                    }
                }

            }
        }
    }

    /**
     * This method returns the property descriptors for the given class as a Map
     */
    public static Map getPropertyDescriptors(Class targetClass)
            throws IntrospectionException, OgnlException
    {
        Map result;

        synchronized (propertyDescriptorCache) {
            if ((result = (Map) propertyDescriptorCache.get(targetClass)) == null) {
                PropertyDescriptor[] pda = Introspector.getBeanInfo(targetClass).getPropertyDescriptors();

                result = new HashMap(101);
                for (int i = 0, icount = pda.length; i < icount; i++) {
                    result.put(pda[i].getName(), pda[i]);
                }
                
                findObjectIndexedPropertyDescriptors(targetClass, result);
                propertyDescriptorCache.put(targetClass, result);
            }
        }

        return result;
    }

    /**
     * This method returns a PropertyDescriptor for the given class and property name using a Map
     * lookup (using getPropertyDescriptorsMap()).
     */
    public static PropertyDescriptor getPropertyDescriptor(Class targetClass, String propertyName)
            throws IntrospectionException, OgnlException
    {
        if (targetClass == null)
            return null;

        return (PropertyDescriptor) getPropertyDescriptors(targetClass).get(propertyName);
    }

    public static PropertyDescriptor[] getPropertyDescriptorsArray(Class targetClass)
            throws IntrospectionException
    {
        PropertyDescriptor[] result = null;

        if (targetClass != null) {
            synchronized (propertyDescriptorCache) {
                if ((result = (PropertyDescriptor[]) propertyDescriptorCache.get(targetClass)) == null) {
                    propertyDescriptorCache.put(targetClass, result = Introspector.getBeanInfo(targetClass)
                            .getPropertyDescriptors());
                }
            }
        }
        return result;
    }

    /**
     * Gets the property descriptor with the given name for the target class given.
     *
     * @param targetClass Class for which property descriptor is desired
     * @param name        Name of property
     * @return PropertyDescriptor of the named property or null if the class has no property with
     *         the given name
     */
    public static PropertyDescriptor getPropertyDescriptorFromArray(Class targetClass, String name)
            throws IntrospectionException
    {
        PropertyDescriptor result = null;
        PropertyDescriptor[] pda = getPropertyDescriptorsArray(targetClass);

        for (int i = 0, icount = pda.length; (result == null) && (i < icount); i++) {
            if (pda[i].getName().compareTo(name) == 0) {
                result = pda[i];
            }
        }
        return result;
    }

    public static void setMethodAccessor(Class cls, MethodAccessor accessor)
    {
        synchronized (methodAccessors) {
            methodAccessors.put(cls, accessor);
        }
    }

    public static MethodAccessor getMethodAccessor(Class cls)
            throws OgnlException
    {
        MethodAccessor answer = (MethodAccessor) getHandler(cls, methodAccessors);
        if (answer != null)
            return answer;
        throw new OgnlException("No method accessor for " + cls);
    }

    public static void setPropertyAccessor(Class cls, PropertyAccessor accessor)
    {
        synchronized (propertyAccessors) {
            propertyAccessors.put(cls, accessor);
        }
    }

    public static PropertyAccessor getPropertyAccessor(Class cls)
            throws OgnlException
    {
        PropertyAccessor answer = (PropertyAccessor) getHandler(cls, propertyAccessors);
        if (answer != null)
            return answer;

        throw new OgnlException("No property accessor for class " + cls);
    }

    public static ElementsAccessor getElementsAccessor(Class cls)
            throws OgnlException
    {
        ElementsAccessor answer = (ElementsAccessor) getHandler(cls, elementsAccessors);
        if (answer != null)
            return answer;
        throw new OgnlException("No elements accessor for class " + cls);
    }

    public static void setElementsAccessor(Class cls, ElementsAccessor accessor)
    {
        synchronized (elementsAccessors) {
            elementsAccessors.put(cls, accessor);
        }
    }

    public static NullHandler getNullHandler(Class cls)
            throws OgnlException
    {
        NullHandler answer = (NullHandler) getHandler(cls, nullHandlers);
        if (answer != null)
            return answer;
        throw new OgnlException("No null handler for class " + cls);
    }

    public static void setNullHandler(Class cls, NullHandler handler)
    {
        synchronized (nullHandlers) {
            nullHandlers.put(cls, handler);
        }
    }

    private static Object getHandler(Class forClass, ClassCache handlers)
    {
        Object answer = null;

        synchronized (handlers) {
            if ((answer = handlers.get(forClass)) == null) {
                Class keyFound;

                if (forClass.isArray()) {
                    answer = handlers.get(Object[].class);
                    keyFound = null;
                } else {
                    keyFound = forClass;
                    outer:
                    for (Class c = forClass; c != null; c = c.getSuperclass()) {
                        answer = handlers.get(c);
                        if (answer == null) {
                            Class[] interfaces = c.getInterfaces();
                            for (int index = 0, count = interfaces.length; index < count; ++index) {
                                Class iface = interfaces[index];

                                answer = handlers.get(iface);
                                if (answer == null) {
                                    /* Try super-interfaces */
                                    answer = getHandler(iface, handlers);
                                }
                                if (answer != null) {
                                    keyFound = iface;
                                    break outer;
                                }
                            }
                        } else {
                            keyFound = c;
                            break;
                        }
                    }
                }
                if (answer != null) {
                    if (keyFound != forClass) {
                        handlers.put(forClass, answer);
                    }
                }
            }
        }
        return answer;
    }

    public static Object getProperty(OgnlContext context, Object source, Object name)
            throws OgnlException
    {
        PropertyAccessor accessor;

        if (source == null) {
            throw new OgnlException("source is null for getProperty(null, \"" + name + "\")");
        }
        if ((accessor = getPropertyAccessor(getTargetClass(source))) == null) {
            throw new OgnlException(
                    "No property accessor for " + getTargetClass(source).getName());
        }
        return accessor.getProperty(context, source, name);
    }

    public static void setProperty(OgnlContext context, Object target, Object name, Object value)
            throws OgnlException
    {
        PropertyAccessor accessor;

        if (target == null) {
            throw new OgnlException("target is null for setProperty(null, \"" + name + "\", " + value
                    + ")");
        }
        if ((accessor = getPropertyAccessor(getTargetClass(target))) == null) {
            throw new OgnlException(
                    "No property accessor for " + getTargetClass(target).getName());
        }
        accessor.setProperty(context, target, name, value);
    }

    /**
     * Determines the index property type, if any. Returns <code>INDEXED_PROPERTY_NONE</code> if
     * the property is not index-accessible as determined by OGNL or JavaBeans. If it is indexable
     * then this will return whether it is a JavaBeans indexed property, conforming to the indexed
     * property patterns (returns <code>INDEXED_PROPERTY_INT</code>) or if it conforms to the
     * OGNL arbitrary object indexable (returns <code>INDEXED_PROPERTY_OBJECT</code>).
     */
    public static int getIndexedPropertyType(OgnlContext context, Class sourceClass, String name)
            throws OgnlException
    {
        int result = INDEXED_PROPERTY_NONE;

        try {
            PropertyDescriptor pd = getPropertyDescriptor(sourceClass, name);
            if (pd != null) {
                if (pd instanceof IndexedPropertyDescriptor) {
                    result = INDEXED_PROPERTY_INT;
                } else {
                    if (pd instanceof ObjectIndexedPropertyDescriptor) {
                        result = INDEXED_PROPERTY_OBJECT;
                    }
                }
            }
        } catch (Exception ex) {
            throw new OgnlException("problem determining if '" + name + "' is an indexed property", ex);
        }
        return result;
    }

    public static Object getIndexedProperty(OgnlContext context, Object source, String name, Object index)
            throws OgnlException
    {
        Object[] args = objectArrayPool.create(index);

        try {
            PropertyDescriptor pd = getPropertyDescriptor((source == null) ? null : source.getClass(), name);
            Method m;

            if (pd instanceof IndexedPropertyDescriptor) {
                m = ((IndexedPropertyDescriptor) pd).getIndexedReadMethod();
            } else {
                if (pd instanceof ObjectIndexedPropertyDescriptor) {
                    m = ((ObjectIndexedPropertyDescriptor) pd).getIndexedReadMethod();
                } else {
                    throw new OgnlException("property '" + name + "' is not an indexed property");
                }
            }

            return callMethod(context, source, m.getName(), name, args);

        } catch (OgnlException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OgnlException("getting indexed property descriptor for '" + name + "'", ex);
        } finally {
            objectArrayPool.recycle(args);
        }
    }

    public static void setIndexedProperty(OgnlContext context, Object source, String name, Object index,
                                                Object value)
            throws OgnlException
    {
        Object[] args = objectArrayPool.create(index, value);

        try {
            PropertyDescriptor pd = getPropertyDescriptor((source == null) ? null : source.getClass(), name);
            Method m;

            if (pd instanceof IndexedPropertyDescriptor) {
                m = ((IndexedPropertyDescriptor) pd).getIndexedWriteMethod();
            } else {
                if (pd instanceof ObjectIndexedPropertyDescriptor) {
                    m = ((ObjectIndexedPropertyDescriptor) pd).getIndexedWriteMethod();
                } else {
                    throw new OgnlException("property '" + name + "' is not an indexed property");
                }
            }

            callMethod(context, source, m.getName(), name, args);

        } catch (OgnlException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OgnlException("getting indexed property descriptor for '" + name + "'", ex);
        } finally {
            objectArrayPool.recycle(args);
        }
    }

    public static EvaluationPool getEvaluationPool()
    {
        return evaluationPool;
    }

    public static ObjectArrayPool getObjectArrayPool()
    {
        return objectArrayPool;
    }

    public static Method getMethod(OgnlContext context, Class target, String name, Node[] children, boolean includeStatic)
            throws Exception
    {
        Class[] parms = null;
        if (children != null && children.length > 0) {

            parms = new Class[children.length];
            for (int i = 0; i < children.length; i++) {
                if (!NodeType.class.isInstance(children[i]))
                    throw new UnsupportedCompilationException("Unable to determine parameter types for method.");

                NodeType type = (NodeType) children[i];
                parms[i] = type.getGetterClass();
            }

        } else
            parms = new Class[0];

        List methods = OgnlRuntime.getMethods(target, name, includeStatic);
        if (methods == null)
            return null;

        for (int i = 0; i < methods.size(); i++) {
            Method m = (Method) methods.get(i);

            if (parms.length != m.getParameterTypes().length)
                continue;

            Class[] mparms = m.getParameterTypes();
            boolean matched = true;
            for (int p = 0; p < mparms.length; p++) {

                if (parms[p] == null) {
                    matched = false;
                    break;
                }

                if (parms[p] == mparms[p])
                    continue;

                if (mparms[p].isPrimitive()
                        && Character.TYPE != mparms[p] && Byte.TYPE != mparms[p]
                        && Number.class.isAssignableFrom(parms[p])
                        && OgnlRuntime.getPrimitiveWrapperClass(parms[p]) == mparms[p]) {
                    continue;
                }

                matched = false;
                break;
            }

            if (matched)
                return m;
        }

        return null;
    }

    public static Method getReadMethod(Class target, String name)
    {
        return getReadMethod(target, name, -1);
    }

    public static Method getReadMethod(Class target, String name, int numParms)
    {
        try {
            name = name.replaceAll("\"", "");

            BeanInfo info = Introspector.getBeanInfo(target);

            MethodDescriptor[] methods = info.getMethodDescriptors();

            // exact matches first
            for (int i = 0; i < methods.length; i++) {

                if ((methods[i].getName().equalsIgnoreCase(name)
                        || methods[i].getName().toLowerCase().equals(name.toLowerCase())
                        || methods[i].getName().toLowerCase().equals("get" + name.toLowerCase()))
                        && !methods[i].getName().startsWith("set")) {

                    if (numParms > 0 && methods[i].getMethod().getParameterTypes().length == numParms)
                        return methods[i].getMethod();
                    else if (numParms < 0)
                        return methods[i].getMethod();
                }
            }

            for (int i = 0; i < methods.length; i++) {

                //System.out.println("checking for read method " + name + " in " + methods[i].getName());
                if (methods[i].getName().toLowerCase().endsWith(name.toLowerCase())
                        && !methods[i].getName().startsWith("set")) {

                    if (numParms > 0 && methods[i].getMethod().getParameterTypes().length == numParms)
                        return methods[i].getMethod();
                    else if (numParms < 0)
                        return methods[i].getMethod();
                }
            }

            // try one last time adding a get to beginning

            if (!name.startsWith("get")) {

                return OgnlRuntime.getReadMethod(target, "get" + name, numParms);
            }

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        return null;
    }

    public static Method getWriteMethod(Class target, String name)
    {
        return getWriteMethod(target, name, -1);
    }

    public static Method getWriteMethod(Class target, String name, int numParms)
    {
        try {
            name = name.replaceAll("\"", "");

            BeanInfo info = Introspector.getBeanInfo(target);

            MethodDescriptor[] methods = info.getMethodDescriptors();

            for (int i = 0; i < methods.length; i++) {
                //System.out.println("checking for write method " + name + " in " + methods[i].getName());
                if ((methods[i].getName().equalsIgnoreCase(name)
                        || methods[i].getName().toLowerCase().equals(name.toLowerCase())
                        || methods[i].getName().toLowerCase().equals("set" + name.toLowerCase()))
                        && !methods[i].getName().startsWith("get")) {

                    if (numParms > 0 && methods[i].getMethod().getParameterTypes().length == numParms)
                        return methods[i].getMethod();
                    else if (numParms < 0)
                        return methods[i].getMethod();
                }
            }
            
            // try one last time adding a set to beginning

            if (!name.startsWith("set")) {

                return OgnlRuntime.getReadMethod(target, "set" + name, numParms);
            }

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        return null;
    }

    public static PropertyDescriptor getProperty(Class target, String name)
    {
        try {
            BeanInfo info = Introspector.getBeanInfo(target);

            PropertyDescriptor[] pds = info.getPropertyDescriptors();

            for (int i = 0; i < pds.length; i++) {
                //System.out.println("checking for " + name + " in " + pds[i].getShortDescription());
                if (pds[i].getName().equalsIgnoreCase(name)
                        || pds[i].getName().toLowerCase().equals(name.toLowerCase())
                        || pds[i].getName().toLowerCase().endsWith(name.toLowerCase()))
                    return pds[i];
            }

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        return null;
    }

    public static Class getSuperOrInterfaceClass(Method m, Class clazz)
    {
        if (clazz.getSuperclass() != null) {
            Class superClass = getSuperOrInterfaceClass(m, clazz.getSuperclass());

            if (superClass != null)
                return superClass;
        }

        boolean clazzHasMethod = containsMethod(m, clazz);

        if (clazz.getInterfaces() != null && clazz.getInterfaces().length > 0) {

            Class[] intfs = clazz.getInterfaces();
            Class intClass = null;
            for (int i = 0; i < intfs.length; i++) {
                intClass = getSuperOrInterfaceClass(m, intfs[i]);

                if (intClass != null && !clazzHasMethod)
                    return intClass;
            }
        }

        if (clazzHasMethod)
            return clazz;

        return null;
    }

    public static boolean containsMethod(Method m, Class clazz)
    {
        Method[] methods = clazz.getMethods();

        if (methods == null)
            return false;

        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(m.getName())
                    && methods[i].getReturnType() == m.getReturnType()) {

                Class[] parms = m.getParameterTypes();
                if (parms == null)
                    continue;

                Class[] mparms = methods[i].getParameterTypes();
                if (mparms == null || mparms.length != parms.length)
                    continue;

                boolean parmsMatch = true;
                for (int p = 0; p < parms.length; p++) {
                    if (parms[p] != mparms[p]) {
                        parmsMatch = false;
                        break;
                    }
                }

                if (!parmsMatch)
                    continue;

                Class[] exceptions = m.getExceptionTypes();
                if (exceptions == null)
                    continue;

                Class[] mexceptions = methods[i].getExceptionTypes();
                if (mexceptions == null || mexceptions.length != exceptions.length)
                    continue;

                boolean exceptionsMatch = true;
                for (int e = 0; e < exceptions.length; e++) {
                    if (exceptions[e] != mexceptions[e]) {
                        exceptionsMatch = false;
                        break;
                    }
                }

                if (!exceptionsMatch)
                    continue;

                return true;
            }
        }

        return false;
    }

    /**
     * Compares the {@link OgnlContext#getCurrentType()} and {@link OgnlContext#getPreviousType()} class types
     * on the stack to determine if a numeric expression should force object conversion.
     * <p/>
     * <p/>
     * Normally used in conjunction with the <code>forceConversion</code> parameter of
     * {@link OgnlRuntime#getChildSource(OgnlContext,Object,Node,boolean)}.
     * </p>
     *
     * @param context The current context.
     * @return True, if the class types on the stack wouldn't be comparable in a pure numeric expression such as <code>o1 >= o2</code>.
     */
    public static boolean shouldConvertNumericTypes(OgnlContext context)
    {
        return context.getCurrentType() != null && !context.getCurrentType().isArray()
                && context.getPreviousType() != null && !context.getPreviousType().isArray()
                && !(context.getCurrentType().isPrimitive() && context.getPreviousType().isPrimitive())
                && (!Number.class.isAssignableFrom(context.getCurrentType())
                || !Number.class.isAssignableFrom(context.getPreviousType()));

    }

    /**
     * Attempts to get the java source string represented by the specific child expression
     * via the {@link JavaSource#toGetSourceString(OgnlContext,Object)} interface method.
     *
     * @param context The ognl context to pass to the child.
     * @param target  The current object target to use.
     * @param child   The child expression.
     * @return The result of calling {@link JavaSource#toGetSourceString(OgnlContext,Object)} plus additional
     *         enclosures of {@link OgnlOps#convertValue(Object,Class,boolean)} for conversions.
     * @throws OgnlException Mandatory exception throwing catching.. (blehh)
     */
    public static String getChildSource(OgnlContext context, Object target, Node child)
            throws OgnlException
    {
        return getChildSource(context, target, child, false);
    }

    /**
     * Attempts to get the java source string represented by the specific child expression
     * via the {@link JavaSource#toGetSourceString(OgnlContext,Object)} interface method.
     *
     * @param context         The ognl context to pass to the child.
     * @param target          The current object target to use.
     * @param child           The child expression.
     * @param forceConversion If true, forces {@link OgnlOps#convertValue(Object,Class)} conversions on the objects.
     * @return The result of calling {@link JavaSource#toGetSourceString(OgnlContext,Object)} plus additional
     *         enclosures of {@link OgnlOps#convertValue(Object,Class,boolean)} for conversions.
     * @throws OgnlException Mandatory exception throwing catching.. (blehh)
     */
    public static String getChildSource(OgnlContext context, Object target, Node child, boolean forceConversion)
            throws OgnlException
    {
        String pre = (String) context.get("_currentChain");
        if (pre == null)
            pre = "";

        try {

            child.getValue(context, target);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        String source = child.toGetSourceString(context, target);

        // System.out.println("getChildSource class: " + child.getClass().getName() + " source: " + source);

        // handle root / method expressions that may not have proper root java source access

        source = pre + source;

        if (ASTProperty.class.isInstance(child)) {

            source = ExpressionCompiler.getRootExpression(child, context.getRoot(), false) + source;
            context.setCurrentAccessor(context.getRoot().getClass());

        } else if (ASTMethod.class.isInstance(child)) {

            source = ExpressionCompiler.getRootExpression(child, context.getRoot(), false) + source;

            context.setCurrentAccessor(context.getRoot().getClass());

        } else if (ASTChain.class.isInstance(child)) {

            source = ExpressionCompiler.getRootExpression(child, context.getRoot(), false) + source;
            context.setCurrentAccessor(context.getRoot().getClass());

            String cast = (String) context.remove(ExpressionCompiler.PRE_CAST);
            if (cast == null)
                cast = "";

            source = cast + source;
        }

        if (source == null || source.trim().length() < 1)
            source = "null";

        //System.out.println("getChildSource  currentType: " + context.getCurrentType() + " previousType: " + context.getPreviousType());

        if (context.getCurrentType() != null && !context.getCurrentType().isArray()
                && Number.class.isAssignableFrom(context.getCurrentType())
                && (child.jjtGetParent() == null
                || (forceConversion
                || (ASTOr.class.isAssignableFrom(child.jjtGetParent().getClass())
                || ASTAnd.class.isAssignableFrom(child.jjtGetParent().getClass())
                || !BooleanExpression.class.isAssignableFrom(child.jjtGetParent().getClass()))
                && !ASTTest.class.isAssignableFrom(child.jjtGetParent().getClass())))) {

            source = "ognl.OgnlOps.convertValue(" + source + ", " + context.getCurrentType().getName() + ".class)";

        } else if (context.getCurrentType() != null && !context.getCurrentType().isArray()
                && context.getCurrentType() != Boolean.TYPE
                && context.getCurrentType().isPrimitive()
                && (child.jjtGetParent() == null
                || (forceConversion
                || ASTOr.class.isAssignableFrom(child.jjtGetParent().getClass())
                || ASTAnd.class.isAssignableFrom(child.jjtGetParent().getClass()))
                || !BooleanExpression.class.isAssignableFrom(child.jjtGetParent().getClass()))) {

            source = "ognl.OgnlOps.convertValue(" + source + ", " + OgnlRuntime.getPrimitiveWrapperClass(context.getCurrentType()).getName() + ".class)";
        }

        return source;
    }
}