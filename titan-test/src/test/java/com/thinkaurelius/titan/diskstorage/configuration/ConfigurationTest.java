package com.thinkaurelius.titan.diskstorage.configuration;

import com.google.common.collect.ImmutableSet;
import com.thinkaurelius.titan.diskstorage.configuration.backend.CommonsConfiguration;
import org.apache.commons.configuration.BaseConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class ConfigurationTest {


    @Test
    public void testConfigHierarchy() {
        ConfigNamespace root = new ConfigNamespace(null,"config","root");
        ConfigNamespace indexes = new ConfigNamespace(root,"indexes","Index definitions",true);
        ConfigNamespace storage = new ConfigNamespace(root,"storage","Storage definitions");
        ConfigNamespace special = new ConfigNamespace(storage,"special","Special storage definitions");
        ConfigOption<String[]> hostnames = new ConfigOption<String[]>(storage,"hostname","Storage backend hostname",
                ConfigOption.Type.LOCAL, String[].class);
        ConfigOption<Boolean> partition = new ConfigOption<Boolean>(storage,"partition","whether to enable partition",
                ConfigOption.Type.MASKABLE, false);
        ConfigOption<Long> locktime = new ConfigOption<Long>(storage,"locktime","how long to lock",
                ConfigOption.Type.FIXED, 500l);
        ConfigOption<Byte> bits = new ConfigOption<Byte>(storage,"bits","number of unique bits",
                ConfigOption.Type.GLOBAL_OFFLINE, (byte)8);
        ConfigOption<Short> retry = new ConfigOption<Short>(special,"retry","retry wait time",
                ConfigOption.Type.GLOBAL, (short)200);
        ConfigOption<Double> bar = new ConfigOption<Double>(special,"bar","bar",
                ConfigOption.Type.GLOBAL, 1.5d);


        ConfigOption<String> indexback = new ConfigOption<String>(indexes,"name","index name",
                ConfigOption.Type.MASKABLE, String.class);
        ConfigOption<Integer> ping = new ConfigOption<Integer>(indexes,"ping","ping time",
                ConfigOption.Type.LOCAL, 100);
        ConfigOption<Boolean> presort = new ConfigOption<Boolean>(indexes,"presort","presort result set",
                ConfigOption.Type.LOCAL, false);

        //Local configuration
        ModifiableConfiguration config = new ModifiableConfiguration(root,new CommonsConfiguration(new BaseConfiguration()),false);
        UserModifiableConfiguration userconfig = new UserModifiableConfiguration(config);
        assertFalse(config.get(partition));
        assertEquals("false", userconfig.get("storage.partition"));
        userconfig.set("storage.partition",true);
        assertEquals("true", userconfig.get("storage.partition"));
        userconfig.set("storage.hostname", new String[]{"localhost", "some.where.org"});
        assertEquals("[localhost,some.where.org]", userconfig.get("storage.hostname"));
        userconfig.set("storage.hostname", "localhost");
        assertEquals("[localhost]", userconfig.get("storage.hostname"));
        assertEquals("", userconfig.get("indexes"));
        userconfig.set("indexes.search.name", "foo");
        assertEquals("+ search", userconfig.get("indexes").trim());
        assertEquals("foo", userconfig.get("indexes.search.name"));
        assertEquals("100", userconfig.get("indexes.search.ping"));
        userconfig.set("indexes.search.ping", 400l);
        assertEquals("400",userconfig.get("indexes.search.ping"));
        assertFalse(config.isFrozenConfiguration());
        try {
            userconfig.set("storage.locktime",500);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            config.set(retry,(short)100);
            fail();
        } catch (IllegalArgumentException e) {}
        System.out.println(userconfig.get("storage"));
        userconfig.close();
        ReadConfiguration localConfig = userconfig.getConfiguration();

        config = new ModifiableConfiguration(root,new CommonsConfiguration(new BaseConfiguration()),true);
        userconfig = new UserModifiableConfiguration(config);

        userconfig.set("storage.locktime",1111);
        userconfig.set("storage.bits",5);
        userconfig.set("storage.special.retry",222);
        assertEquals("5", userconfig.get("storage.bits"));
        assertEquals("222", userconfig.get("storage.special.retry"));

        config.freezeConfiguration();
        userconfig.set("storage.special.retry", 333);
        assertEquals("333", userconfig.get("storage.special.retry"));
        try {
            userconfig.set("storage.bits",6);
        } catch (IllegalArgumentException e) {}
        userconfig.set("storage.bits",6,"force");
        try {
            userconfig.set("storage.locktime",1221);
        } catch (IllegalArgumentException e) {}
        try {
            userconfig.set("storage.locktime",1221,"force");
        } catch (IllegalArgumentException e) {}
        userconfig.set("indexes.find.name","lulu");

        userconfig.close();
        ReadConfiguration globalConfig = userconfig.getConfiguration();

        MixedConfiguration mixed = new MixedConfiguration(root,globalConfig,localConfig);
        assertEquals(ImmutableSet.of("search","find"),mixed.getContainedNamespaces(indexes));
        assertEquals(100,mixed.get(ping,"find").intValue());
        assertEquals(false,mixed.get(presort,"find").booleanValue());
        assertEquals(400,mixed.get(ping,"search").intValue());
        assertEquals(false,mixed.get(presort,"search").booleanValue());

        assertEquals(1.5d,mixed.get(bar).doubleValue());
        assertEquals("localhost",mixed.get(hostnames)[0]);
        assertEquals(1111,mixed.get(locktime).longValue());

        mixed.close();

    }




}