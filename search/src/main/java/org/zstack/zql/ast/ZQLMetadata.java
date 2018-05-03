package org.zstack.zql.ast;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.zstack.header.core.StaticInit;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.query.ExpandedQueries;
import org.zstack.header.query.ExpandedQuery;
import org.zstack.header.query.ExpandedQueryAlias;
import org.zstack.header.query.ExpandedQueryAliases;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.search.Inventory;
import org.zstack.utils.BeanUtils;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.FieldUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

public class ZQLMetadata {
    private static final CLogger logger = Utils.getLogger(ZQLMetadata.class);

    public static class ExpandQueryMetadata {
        public Class selfVOClass;
        public Class targetVOClass;
        public Class targetInventoryClass;
        public String selfKeyName;
        public String targetKeyName;
        public String name;
        public boolean hidden;
    }

    public static class ExpandQueryAliasMetadata {
        public String aliasName;
        public String expandQueryText;
    }

    public static class InventoryMetadata {
        public Class selfInventoryClass;
        public transient Inventory inventoryAnnotation;
        public Map<String, ExpandQueryMetadata> expandQueries = new HashMap<>();
        public Map<String, ExpandQueryAliasMetadata> expandQueryAliases = new HashMap<>();
        public Set<String> selfInventoryFieldNames;

        public boolean hasInventoryField(String fname) {
            return selfInventoryFieldNames.contains(fname);
        }

        public void errorIfNoField(String fname) {
            if (!hasInventoryField(fname)) {
                throw new CloudRuntimeException(String.format("inventory[${selfInventoryClass}] has no field[%s]", fname));
            }
        }

        public boolean isUs(String inventoryName) {
            return selfInventoryClass.getSimpleName().equalsIgnoreCase(inventoryName);
        }

        public String fullInventoryName() {
            return selfInventoryClass.getName();
        }

        public String simpleInventoryName() {
            return selfInventoryClass.getSimpleName();
        }
    }

    /**
     * key: the full class name of inventory, e.g. org.zstack.host.HostInventory
     * value: InventoryMetadata
     */
    public static Map<String, InventoryMetadata> inventoryMetadata = new HashMap<>();

    interface ChainQueryStruct {
        default void verify() {
        }
    }

    public static InventoryMetadata findInventoryMetadata(String queryName) {
        String qname = String.format("%sinventory", queryName);
        Optional<InventoryMetadata> opt = inventoryMetadata.values().stream().filter(i->i.isUs(qname)).findFirst();
        DebugUtils.Assert(opt.isPresent(), String.format("cannot find inventory with name[%s]", queryName));
        return opt.get();
    }

    public static InventoryMetadata getInventoryMetadataByName(String name) {
        InventoryMetadata m = inventoryMetadata.get(name);
        DebugUtils.Assert(m != null, String.format("cannot find metadata for inventory class[%s]", name));
        return m;
    }

    public static class FieldChainQuery implements ChainQueryStruct {
        InventoryMetadata self;
        ExpandQueryMetadata right;
        String fieldName;

        @Override
        public void verify() {
            if (right != null) {
                InventoryMetadata him = getInventoryMetadataByName(right.targetInventoryClass.getName());
                if (!him.hasInventoryField(fieldName)) {
                    throw new CloudRuntimeException(String.format("inventory class[%s] not having field[%s]", him.selfInventoryClass, fieldName));
                }
            } else {
                if (!self.hasInventoryField(fieldName)) {
                    throw new CloudRuntimeException(String.format("inventory class[%s] not having field[%s]", self.selfInventoryClass, fieldName));
                }
            }
        }


        @Override
        public String toString() {
            return "FieldChainQuery{" +
                    "self=" + self.selfInventoryClass +
                    ", right=" + right.name +
                    ", fieldName='" + fieldName + '\'' +
                    '}';
        }
    }

    public static class ExpandChainQuery implements ChainQueryStruct {
        String selfKey;
        InventoryMetadata self;
        ExpandQueryMetadata right;


        @Override
        public String toString() {
            return "ExpandChainQuery{" +
                    "selfKey='" + selfKey + '\'' +
                    ", self=" + self.selfInventoryClass +
                    ", right=" + right.name +
                    '}';
        }
    }

    public static List<ChainQueryStruct> createChainQuery(String inventoryName, List<String> nestConditionNames) {
        return new ChainQueryStructGetter(inventoryName, nestConditionNames).get();
    }

    private static class ChainQueryStructGetter {
        String inventoryName;
        List<String> nestConditionNames;

        public ChainQueryStructGetter(String inventoryName, List<String> nestConditionNames) {
            this.inventoryName = inventoryName;
            this.nestConditionNames = nestConditionNames;
        }

        List<ChainQueryStruct> get() {
            DebugUtils.Assert(!nestConditionNames.isEmpty(), String.format("empty nestConditionNames for inventoryName[%s]", inventoryName));

            InventoryMetadata metadata = inventoryMetadata.get(inventoryName);
            if (metadata == null) {
                throw new CloudRuntimeException(String.format("cannot find metadata for query target[%s]", inventoryName));
            }

            List<ChainQueryStruct> ret = new ArrayList<>();

            if (nestConditionNames.size() == 1) {
                FieldChainQuery f = new FieldChainQuery();
                f.self = metadata;
                f.fieldName = nestConditionNames.get(0);
                ret.add(f);
            } else {
                String lastField = nestConditionNames.get(nestConditionNames.size()-1);

                List<String> processedConditionsNames = new ArrayList<>();
                preProcessingNestConditionNames(
                        metadata,
                        nestConditionNames.subList(0, nestConditionNames.size()-1).iterator(),
                        processedConditionsNames
                );

                Iterator<String> iterator = processedConditionsNames.iterator();

                InventoryMetadata self = metadata;
                ExpandQueryMetadata left = null;
                while (iterator.hasNext()) {
                    String expandedQueryName = iterator.next();
                    ExpandQueryMetadata e = self.expandQueries.get(expandedQueryName);
                    if (e == null) {
                        throw new CloudRuntimeException(String.format("no expand query[%s] found on %s", expandedQueryName, self.selfInventoryClass));
                    }
                    ExpandChainQuery em = new ExpandChainQuery();
                    em.selfKey = left == null ? null : left.targetKeyName;
                    em.self = self;
                    em.right = e;
                    self = getInventoryMetadataByName(em.right.targetInventoryClass.getName());
                    left = em.right;
                    ret.add(em);
                }

                ExpandChainQuery last = (ExpandChainQuery) ret.get(ret.size()-1);
                FieldChainQuery f = new FieldChainQuery();
                f.right = last.right;
                f.self = last.self;
                f.fieldName = lastField;
                ret.add(f);
            }

            ret.forEach(ChainQueryStruct::verify);

            return ret;
        }

        private void preProcessingNestConditionNames(InventoryMetadata current, Iterator<String> names, List<String> result) {
            if (!names.hasNext()) {
                return;
            }

            String name = names.next();
            ExpandQueryAliasMetadata alias = current.expandQueryAliases.get(name);
            if (alias != null) {
                List<String> newNames = asList(alias.expandQueryText.split("\\."));
                newNames.addAll(Lists.newArrayList(names));
                preProcessingNestConditionNames(current, newNames.iterator(), result);
            } else {
                ExpandQueryMetadata expand = current.expandQueries.get(name);

                if (expand == null) {
                    throw new CloudRuntimeException(String.format("invalid nested query condition[%s] on %s," +
                            "the expanded target[%s] have no expanded query[%s]",
                            StringUtils.join(names, "."),
                            current.selfInventoryClass,
                            current.selfInventoryClass,
                            name
                    ));
                }

                current = inventoryMetadata.get(expand.targetInventoryClass.getName());
                if (current == null) {
                    throw new CloudRuntimeException(String.format("unable to find inventory metadata for %s", expand.targetInventoryClass));
                }

                result.add(name);
                preProcessingNestConditionNames(current, names, result);
            }
        }
    }

    private static void fillInventoryMetadata(Class clz,
                                              List<ExpandedQuery> queries, List<ExpandedQueryAlias> aliases,
                                              List<ExpandedQuery> queryForOther, List<ExpandedQueryAlias> aliasForOther) {
        Inventory inventory = (Inventory) clz.getAnnotation(Inventory.class);

        if (inventory == null) {
            throw new CloudRuntimeException(String.format("class[%s] not annotated by @Inventory", clz));
        }

        InventoryMetadata metadata = inventoryMetadata.computeIfAbsent(clz.getName(), x-> {
            InventoryMetadata m = new InventoryMetadata();
            m.inventoryAnnotation = inventory;
            m.selfInventoryClass = clz;
            m.selfInventoryFieldNames = FieldUtils.getAllFields(clz).stream()
                    .filter(i->!i.isAnnotationPresent(APINoSee.class))
                    .map(Field::getName)
                    .collect(Collectors.toSet());
            return m;
        });

        if (queries != null) {
            queries.forEach(it-> {
                if (it.target() != Object.class && it.target() != clz) {
                    if (queryForOther == null) {
                        throw new CloudRuntimeException(String.format("found %s has an expanded query with target[%s], but queryForOther == null", clz, it.target()));
                    }

                    queryForOther.add(it);
                    return;
                }

                Class targetInventoryClass = it.inventoryClass();
                if (!targetInventoryClass.isAnnotationPresent(Inventory.class)) {
                    throw new CloudRuntimeException(String.format("inventory class[%s] is query expanded by %s but not have @Inventory annotation", targetInventoryClass, clz));
                }

                ExpandQueryMetadata emetadata = new ExpandQueryMetadata();
                emetadata.selfVOClass = inventory.mappingVOClass();
                emetadata.targetVOClass = ((Inventory)targetInventoryClass.getAnnotation(Inventory.class)).mappingVOClass();
                emetadata.targetInventoryClass = it.inventoryClass();
                emetadata.selfKeyName = it.foreignKey();
                emetadata.targetKeyName = it.expandedInventoryKey();
                emetadata.name = it.expandedField();
                emetadata.hidden = it.hidden();

                metadata.expandQueries.put(emetadata.name, emetadata);
            });
        }

        if (aliases != null) {
            aliases.forEach(it -> {
                if (it.target() != Object.class && it.target() != clz) {
                    if (queryForOther == null) {
                        throw new CloudRuntimeException(String.format("found %s has an expanded alias with target[%s], but aliasForOther == null", clz, it.target()));
                    }
                    aliasForOther.add(it);
                    return;
                }

                ExpandQueryAliasMetadata e = new ExpandQueryAliasMetadata();
                e.aliasName = it.alias();
                e.expandQueryText = it.expandedField();
                metadata.expandQueryAliases.put(it.alias(), e);
            });
        }

        inventoryMetadata.put(clz.getName(), metadata);
    }

    @StaticInit
    static void staticInit() {
        List<ExpandedQuery> queryForOther = new ArrayList<>();
        List<ExpandedQueryAlias> aliasForOther = new ArrayList<>();

        BeanUtils.reflections.getTypesAnnotatedWith(Inventory.class).stream().filter(i->i.isAnnotationPresent(Inventory.class))
                .forEach(clz -> {
                    ExpandedQueries queries = clz.getAnnotation(ExpandedQueries.class);
                    ExpandedQueryAliases aliases = clz.getAnnotation(ExpandedQueryAliases.class);
                    fillInventoryMetadata(clz,
                            queries != null ? asList(queries.value())  : null,
                            aliases != null ? asList(aliases.value()) : null,
                            queryForOther, aliasForOther);
                });

        queryForOther.forEach(it -> {
            Class clz = it.target();
            fillInventoryMetadata(clz, asList(it), null,  null, null);
        });

        aliasForOther.forEach(it -> {
            Class clz = it.target();
            fillInventoryMetadata(clz, null, asList(it),  null, null);
        });
    }
}
