/*
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-$year$ Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package com.jivesoftware.os.tasmo.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.jivesoftware.os.jive.utils.base.interfaces.CallbackStream;
import com.jivesoftware.os.jive.utils.id.ChainedVersion;
import com.jivesoftware.os.jive.utils.id.Id;
import com.jivesoftware.os.jive.utils.id.IdProvider;
import com.jivesoftware.os.jive.utils.id.ImmutableByteArray;
import com.jivesoftware.os.jive.utils.id.ObjectId;
import com.jivesoftware.os.jive.utils.id.TenantId;
import com.jivesoftware.os.jive.utils.id.TenantIdAndCentricId;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.jive.utils.row.column.value.store.api.ColumnValueAndTimestamp;
import com.jivesoftware.os.jive.utils.row.column.value.store.api.RowColumnValueStore;
import com.jivesoftware.os.jive.utils.row.column.value.store.inmemory.RowColumnValueStoreImpl;
import com.jivesoftware.os.tasmo.configuration.BindingGenerator;
import com.jivesoftware.os.tasmo.configuration.ViewModel;
import com.jivesoftware.os.tasmo.configuration.views.TenantViewsProvider;
import com.jivesoftware.os.tasmo.event.api.JsonEventConventions;
import com.jivesoftware.os.tasmo.event.api.write.Event;
import com.jivesoftware.os.tasmo.event.api.write.EventWriteException;
import com.jivesoftware.os.tasmo.event.api.write.EventWriter;
import com.jivesoftware.os.tasmo.event.api.write.EventWriterOptions;
import com.jivesoftware.os.tasmo.event.api.write.EventWriterResponse;
import com.jivesoftware.os.tasmo.event.api.write.JsonEventWriteException;
import com.jivesoftware.os.tasmo.event.api.write.JsonEventWriter;
import com.jivesoftware.os.tasmo.id.IdProviderImpl;
import com.jivesoftware.os.tasmo.lib.concur.ConcurrencyAndExistenceCommitChange;
import com.jivesoftware.os.tasmo.lib.events.EventValueStore;
import com.jivesoftware.os.tasmo.lib.process.WrittenEventContext;
import com.jivesoftware.os.tasmo.lib.process.WrittenEventProcessor;
import com.jivesoftware.os.tasmo.lib.process.WrittenEventProcessorDecorator;
import com.jivesoftware.os.tasmo.lib.process.WrittenInstanceHelper;
import com.jivesoftware.os.tasmo.lib.process.bookkeeping.BookkeepingEvent;
import com.jivesoftware.os.tasmo.lib.process.bookkeeping.EventBookKeeper;
import com.jivesoftware.os.tasmo.lib.process.bookkeeping.TasmoEventBookkeeper;
import com.jivesoftware.os.tasmo.lib.process.notification.ViewChangeNotificationProcessor;
import com.jivesoftware.os.tasmo.lib.read.ReadMaterializedViewProvider;
import com.jivesoftware.os.tasmo.lib.write.CommitChange;
import com.jivesoftware.os.tasmo.lib.write.CommitChangeException;
import com.jivesoftware.os.tasmo.lib.write.PathId;
import com.jivesoftware.os.tasmo.lib.write.ViewFieldChange;
import com.jivesoftware.os.tasmo.lib.write.read.EventValueStoreFieldValueReader;
import com.jivesoftware.os.tasmo.model.ViewBinding;
import com.jivesoftware.os.tasmo.model.Views;
import com.jivesoftware.os.tasmo.model.ViewsProcessorId;
import com.jivesoftware.os.tasmo.model.ViewsProvider;
import com.jivesoftware.os.tasmo.model.path.ModelPath;
import com.jivesoftware.os.tasmo.model.path.ModelPathStep;
import com.jivesoftware.os.tasmo.model.path.ModelPathStepType;
import com.jivesoftware.os.tasmo.model.path.MurmurHashViewPathKeyProvider;
import com.jivesoftware.os.tasmo.model.process.JsonWrittenEventProvider;
import com.jivesoftware.os.tasmo.model.process.OpaqueFieldValue;
import com.jivesoftware.os.tasmo.model.process.WrittenEvent;
import com.jivesoftware.os.tasmo.model.process.WrittenEventProvider;
import com.jivesoftware.os.tasmo.reference.lib.ClassAndField_IdKey;
import com.jivesoftware.os.tasmo.reference.lib.ReferenceStore;
import com.jivesoftware.os.tasmo.reference.lib.concur.ConcurrencyStore;
import com.jivesoftware.os.tasmo.reference.lib.concur.HBaseBackedConcurrencyStore;
import com.jivesoftware.os.tasmo.reference.lib.traverser.ReferenceTraverser;
import com.jivesoftware.os.tasmo.reference.lib.traverser.SerialReferenceTraverser;
import com.jivesoftware.os.tasmo.view.reader.api.ViewDescriptor;
import com.jivesoftware.os.tasmo.view.reader.api.ViewResponse;
import com.jivesoftware.os.tasmo.view.reader.service.JsonViewMerger;
import com.jivesoftware.os.tasmo.view.reader.service.StaleViewFieldStream;
import com.jivesoftware.os.tasmo.view.reader.service.ViewAsObjectNode;
import com.jivesoftware.os.tasmo.view.reader.service.ViewPermissionCheckResult;
import com.jivesoftware.os.tasmo.view.reader.service.ViewPermissionChecker;
import com.jivesoftware.os.tasmo.view.reader.service.ViewProvider;
import com.jivesoftware.os.tasmo.view.reader.service.ViewValueReader;
import com.jivesoftware.os.tasmo.view.reader.service.shared.ViewValue;
import com.jivesoftware.os.tasmo.view.reader.service.shared.ViewValueStore;
import com.jivesoftware.os.tasmo.view.reader.service.writer.ViewValueWriter;
import com.jivesoftware.os.tasmo.view.reader.service.writer.ViewWriteFieldChange;
import com.jivesoftware.os.tasmo.view.reader.service.writer.ViewWriterException;
import com.jivesoftware.os.tasmo.view.reader.service.writer.WriteToViewValueStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 *
 */
public class BaseTasmoTest {

    public static final TenantId MASTER_TENANT_ID = new TenantId("master");
    IdProvider idProvider;
    OrderIdProvider orderIdProvider;
    TenantId tenantId;
    TenantIdAndCentricId tenantIdAndCentricId;
    Id actorId;
    //UserIdentity userIdentity;
    EventWriter writer;
    EventValueStore eventValueStore;
    ViewValueStore viewValueStore;
    ViewValueWriter viewValueWriter;
    ViewValueReader viewValueReader;
    JsonViewMerger merger;
    ViewAsObjectNode viewAsObjectNode;
    ViewPermissionChecker viewPermissionChecker;
    ViewProvider<ViewResponse> viewProvider;
    TasmoViewModel tasmoViewModel;
    TasmoViewMaterializer materializer;
    final ChainedVersion currentVersion = new ChainedVersion("0", "1");
    final AtomicReference<Views> views = new AtomicReference<>();
    ReferenceTraverser referenceTraverser;
    ViewsProvider viewsProvider;
    ReadMaterializedViewProvider<ViewResponse> readMaterializedViewProvider;
    boolean useHBase = false;
    ObjectMapper mapper = new ObjectMapper();

    {
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

    }
    WrittenEventProvider<ObjectNode, JsonNode> writtenEventProvider = new JsonWrittenEventProvider();
    private static BaseTasmoTest base;

    public static BaseTasmoTest create() {
        if (base == null) {
            base = new BaseTasmoTest();
        }
        return base;
    }

    public ViewChangeNotificationProcessor getViewChangeNotificationProcessor() {
        // default is a no op processor
        return new ViewChangeNotificationProcessor() {
            @Override
            public void process(WrittenEventContext batchContext, WrittenEvent writtenEvent) throws Exception {
            }
        };
    }

    public RowColumnValueStoreProvider getRowColumnValueStoreProvider(final String env) {
        if (useHBase) {
//            final SetOfSortedMapsImplInitializer<Exception> hBase = new HBaseSetOfSortedMapsImplInitializer(
//                embeddedHBase.getConfiguration());
//            return new RowColumnValueStoreProvider() {
//
//
//                @Override
//                public RowColumnValueStore<TenantIdAndCentricId, ObjectId, String, OpaqueFieldValue, RuntimeException> eventStore() throws IOException {
//                    return new NeverAcceptsFailureSetOfSortedMaps<>(hBase.initialize(env, "eventValueTable", "v",
//                        new DefaultRowColumnValueStoreMarshaller<>(new TenantIdAndCentricIdMarshaller(),
//                        new ObjectIdMarshaller(), new StringTypeMarshaller(),
//                        writtenEventProvider.getLiteralFieldValueMarshaller()), new CurrentTimestamper()));
//                }
//
//                @Override
//                public RowColumnValueStore<TenantIdAndCentricId, ImmutableByteArray, ImmutableByteArray, ViewValue, RuntimeException> viewValueStore()
//                    throws IOException {
//                    return new NeverAcceptsFailureSetOfSortedMaps<>(hBase.initialize(env, "viewValueTable", "v",
//                        new DefaultRowColumnValueStoreMarshaller<>(new TenantIdAndCentricIdMarshaller(),
//                        new ImmutableByteArrayMarshaller(), new ImmutableByteArrayMarshaller(), new ViewValueMarshaller()),
//                        new CurrentTimestamper()));
//                }
//
//                @Override
//                public RowColumnValueStore<TenantIdAndCentricId, ClassAndField_IdKey, ObjectId, byte[], RuntimeException> multiLinks()
//                    throws IOException {
//                    return new NeverAcceptsFailureSetOfSortedMaps<>(hBase.initialize(env, "multiLinkTable", "v",
//                        new DefaultRowColumnValueStoreMarshaller<>(new TenantIdAndCentricIdMarshaller(), new ClassAndField_IdKeyMarshaller(),
//                        new ObjectIdMarshaller(), new ByteArrayTypeMarshaller()), new CurrentTimestamper()));
//                }
//
//                @Override
//                public RowColumnValueStore<TenantIdAndCentricId, ClassAndField_IdKey, ObjectId, byte[], RuntimeException> multiBackLinks()
//                    throws IOException {
//                    return new NeverAcceptsFailureSetOfSortedMaps<>(hBase.initialize(env, "multiBackLinkTable", "v",
//                        new DefaultRowColumnValueStoreMarshaller<>(new TenantIdAndCentricIdMarshaller(), new ClassAndField_IdKeyMarshaller(),
//                        new ObjectIdMarshaller(), new ByteArrayTypeMarshaller()), new CurrentTimestamper()));
//                }
//            };
            return null;
        } else {
            return new RowColumnValueStoreProvider() {

                @Override
                public RowColumnValueStore<TenantIdAndCentricId, ObjectId, String, OpaqueFieldValue, RuntimeException> eventStore() {
                    return new RowColumnValueStoreImpl<>();
                }

                @Override
                public RowColumnValueStore<TenantIdAndCentricId, ImmutableByteArray, ImmutableByteArray, ViewValue, RuntimeException> viewValueStore() {
                    return new RowColumnValueStoreImpl<>();
                }

                @Override
                public RowColumnValueStore<TenantIdAndCentricId, ClassAndField_IdKey, ObjectId, byte[], RuntimeException> multiLinks() {
                    return new RowColumnValueStoreImpl<>();
                }

                @Override
                public RowColumnValueStore<TenantIdAndCentricId, ClassAndField_IdKey, ObjectId, byte[], RuntimeException> multiBackLinks() {
                    return new RowColumnValueStoreImpl<>();
                }
            };
        }
    }

    public static interface RowColumnValueStoreProvider {

        RowColumnValueStore<TenantIdAndCentricId, ObjectId, String, OpaqueFieldValue, RuntimeException> eventStore() throws Exception;

        RowColumnValueStore<TenantIdAndCentricId, ImmutableByteArray, ImmutableByteArray, ViewValue, RuntimeException> viewValueStore() throws Exception;

        RowColumnValueStore<TenantIdAndCentricId, ClassAndField_IdKey, ObjectId, byte[], RuntimeException> multiLinks() throws Exception;

        RowColumnValueStore<TenantIdAndCentricId, ClassAndField_IdKey, ObjectId, byte[], RuntimeException> multiBackLinks() throws Exception;
    }
    //private static EmbeddedHBase embeddedHBase = new EmbeddedHBase();

    @BeforeClass
    public void startHBase() throws Exception {
        if (useHBase) {
            //embeddedHBase.start(true);
        }
    }

    @AfterClass
    public void stopHBase() throws Exception {
        if (useHBase) {
            //embeddedHBase.stop();
        }
    }

    private ExecutorService executorService;

    @BeforeMethod
    public void setupModelAndMaterializer() throws Exception {

        orderIdProvider = idProvider();
        idProvider = new IdProviderImpl(orderIdProvider);
        tenantId = new TenantId("test");
        tenantIdAndCentricId = new TenantIdAndCentricId(tenantId, Id.NULL);
        //userIdentity = whoami();
        actorId = new Id(1L);

        String uuid = UUID.randomUUID().toString();


        merger = new JsonViewMerger(new ObjectMapper());
        viewAsObjectNode = new ViewAsObjectNode();

        viewPermissionChecker = new ViewPermissionChecker() {
            @Override
            public ViewPermissionCheckResult check(TenantId tenantId, Id actorId, final Set<Id> permissionCheckTheseIds) {
                //System.out.println("NO-OP permisions check for (" + permissionCheckTheseIds.size() + ") ids.");
                return new ViewPermissionCheckResult() {
                    @Override
                    public Set<Id> allowed() {
                        return permissionCheckTheseIds;
                    }

                    @Override
                    public Set<Id> denied() {
                        return new HashSet<>();
                    }

                    @Override
                    public Set<Id> unknown() {
                        return new HashSet<>();
                    }
                };
            }
        };



        RowColumnValueStoreProvider rowColumnValueStoreProvider = getRowColumnValueStoreProvider(uuid);
        RowColumnValueStore<TenantIdAndCentricId, ObjectId, String, OpaqueFieldValue, RuntimeException> eventStore = rowColumnValueStoreProvider.eventStore();

        RowColumnValueStore<TenantIdAndCentricId, ObjectId, String, Long, RuntimeException> concurrency = new RowColumnValueStoreImpl<>();
        ConcurrencyStore concurrencyStore = new HBaseBackedConcurrencyStore(concurrency);
        eventValueStore = new EventValueStore(concurrencyStore, eventStore);

        viewValueStore = new ViewValueStore(rowColumnValueStoreProvider.viewValueStore(), new MurmurHashViewPathKeyProvider());
        viewValueWriter = new ViewValueWriter(viewValueStore);
        viewValueReader = new ViewValueReader(viewValueStore);

        ReferenceStore referenceStore = new ReferenceStore(concurrencyStore, rowColumnValueStoreProvider.multiLinks(),
                rowColumnValueStoreProvider.multiBackLinks());

        final WriteToViewValueStore writeToViewValueStore = new WriteToViewValueStore(viewValueWriter);
        CommitChange commitChange = new CommitChange() {
            @Override
            public void commitChange(WrittenEventContext batchContext,
                    TenantIdAndCentricId tenantIdAndCentricId,
                    List<ViewFieldChange> changes) throws CommitChangeException {
                List<ViewWriteFieldChange> write = new ArrayList<>(changes.size());
                for (ViewFieldChange change : changes) {
                    try {
                        PathId[] modelPathInstanceIds = change.getModelPathInstanceIds();
                        ObjectId[] ids = new ObjectId[modelPathInstanceIds.length];
                        for (int i = 0; i < ids.length; i++) {
                            ids[i] = modelPathInstanceIds[i].getObjectId();
                        }
                        ViewWriteFieldChange viewWriteFieldChange = new ViewWriteFieldChange(
                                change.getEventId(),
                                tenantIdAndCentricId,
                                change.getActorId(),
                                ViewWriteFieldChange.Type.valueOf(change.getType().name()),
                                change.getViewObjectId(),
                                change.getModelPathIdHashcode(),
                                ids,
                                new ViewValue(change.getModelPathTimestamps(), change.getValue()),
                                change.getTimestamp());
                        write.add(viewWriteFieldChange);
                        //System.out.println("viewWriteFieldChange:" + viewWriteFieldChange);
                    } catch (Exception ex) {
                        throw new CommitChangeException("Failed to add change for the following reason.", ex);
                    }
                }

                try {
                    writeToViewValueStore.write(tenantIdAndCentricId, write);
                } catch (ViewWriterException ex) {
                    throw new CommitChangeException("Failed to write BigInteger?", ex);
                }
            }
        };

        commitChange = new ConcurrencyAndExistenceCommitChange(concurrencyStore, commitChange);

        TasmoEventBookkeeper tasmoEventBookkeeper = new TasmoEventBookkeeper(
                new CallbackStream<List<BookkeepingEvent>>() {
                    @Override
                    public List<BookkeepingEvent> callback(List<BookkeepingEvent> value) throws Exception {
                        return value;
                    }
                });

        viewsProvider = new ViewsProvider() {
            @Override
            public Views getViews(ViewsProcessorId viewsProcessorId) {
                return views.get();
            }

            @Override
            public ChainedVersion getCurrentViewsVersion(TenantId tenantId) {
                return currentVersion;
            }
        };


        executorService = Executors.newFixedThreadPool(8);
        //ListeningExecutorService listeningExecutorService = MoreExecutors.listeningDecorator(executorService);
        ListeningExecutorService listeningExecutorService = MoreExecutors.sameThreadExecutor();


        tasmoViewModel = new TasmoViewModel(MASTER_TENANT_ID,
                viewsProvider,
                new MurmurHashViewPathKeyProvider(),
                referenceStore);

        WrittenEventProcessorDecorator writtenEventProcessorDecorator = new WrittenEventProcessorDecorator() {
            @Override
            public WrittenEventProcessor decorateWrittenEventProcessor(WrittenEventProcessor writtenEventProcessor) {
                return new EventBookKeeper(writtenEventProcessor);
            }
        };

        referenceTraverser = new SerialReferenceTraverser(referenceStore);

        TasmoEventTraverser eventTraverser = new TasmoRetryingEventTraverser(writtenEventProcessorDecorator,
            new OrderIdProviderImpl(new ConstantWriterIdProvider(1)));

        WrittenInstanceHelper writtenInstanceHelper = new WrittenInstanceHelper();

        TasmoWriteFanoutEventPersistor eventPersistor = new TasmoWriteFanoutEventPersistor(writtenEventProvider,
            writtenInstanceHelper, concurrencyStore, eventValueStore, referenceStore);

        TasmoProcessingStats processingStats = new TasmoProcessingStats();
        StatCollectingFieldValueReader fieldValueReader = new StatCollectingFieldValueReader(processingStats,
            new EventValueStoreFieldValueReader(eventValueStore));

        TasmoEventProcessor tasmoEventProcessor = new TasmoEventProcessor(tasmoViewModel,
                eventPersistor,
                writtenEventProvider,
                eventTraverser,
                getViewChangeNotificationProcessor(),
                concurrencyStore,
                referenceStore,
                fieldValueReader,
                referenceTraverser,
                commitChange,
                processingStats);

        materializer = new TasmoViewMaterializer(tasmoEventBookkeeper,
                tasmoEventProcessor,
                listeningExecutorService);

        writer = new EventWriter(jsonEventWriter(materializer, orderIdProvider));


        readMaterializedViewProvider = new ReadMaterializedViewProvider(viewPermissionChecker,
            referenceTraverser,
            new EventValueStoreFieldValueReader(eventValueStore),
            tasmoViewModel,
            viewAsObjectNode,
            merger,
            1024 * 1024 * 10);

    }

    @AfterMethod
    public void shutdownMaterializer() throws Exception {
        executorService.shutdownNow();
    }

    Expectations initModelPaths(ArrayNode views) throws Exception {
        List<ViewBinding> viewBindingsList = new LinkedList<>();
        BindingGenerator bindingGenerator = new BindingGenerator();

        for (JsonNode view : views) {
            ViewModel viewConfiguration = ViewModel.builder((ObjectNode) view).build();
            viewBindingsList.add(bindingGenerator.generate(null, viewConfiguration));
        }
        return initModelPaths(viewBindingsList);
    }

    Expectations initModelPaths(List<ViewBinding> bindings) throws Exception {
        Views newViews = new Views(tenantId, currentVersion, bindings);
        views.set(newViews);

        tasmoViewModel.loadModel(MASTER_TENANT_ID);

        TenantId masterTenantId = new TenantId("master");
        TenantViewsProvider tenantViewsProvider = new TenantViewsProvider(masterTenantId, viewsProvider, new MurmurHashViewPathKeyProvider());
        tenantViewsProvider.loadModel(masterTenantId);

        StaleViewFieldStream staleViewFieldStream = new StaleViewFieldStream() {
            @Override
            public void stream(ViewDescriptor viewDescriptor, ColumnValueAndTimestamp<ImmutableByteArray, ViewValue, Long> value) {
                System.out.println("Encountered stale fields for:" + viewDescriptor + " value:" + value);
            }
        };

        viewProvider = new ViewProvider<>(viewPermissionChecker,
                viewValueReader,
                tenantViewsProvider,
                viewAsObjectNode,
                merger,
                staleViewFieldStream,
                1024 * 1024 * 10);
        return new Expectations(viewValueStore, newViews, new MurmurHashViewPathKeyProvider());

    }

    List<ViewBinding> parseModelPathStrings(List<String> simpleBindings) {
        return parseModelPathStrings(simpleBindings.toArray(new String[simpleBindings.size()]));
    }

    List<ViewBinding> parseModelPathStrings(String... simpleBindings) {
        return parseModelPathStrings(false, simpleBindings);
    }

    List<ViewBinding> parseModelPathStrings(boolean idCentric, String... simpleBindings) {
        ArrayListMultimap<String, ModelPath> viewBindings = ArrayListMultimap.create();

        for (String simpleBinding : simpleBindings) {
            String[] class_pathId_modelPath = toStringArray(simpleBinding, "::");
            List<ModelPath> bindings = viewBindings.get(class_pathId_modelPath[0].trim());

            bindings.add(buildPath(class_pathId_modelPath[1].trim(), class_pathId_modelPath[2].trim()));
        }

        List<ViewBinding> viewBindingsList = Lists.newArrayList();
        for (Map.Entry<String, Collection<ModelPath>> entry : viewBindings.asMap().entrySet()) {
            viewBindingsList.add(new ViewBinding(entry.getKey(), new ArrayList<>(entry.getValue()), false, idCentric, false, null));
        }

        return viewBindingsList;
    }

    Expectations initModelPaths(String... simpleBindings) throws Exception {
        List<ViewBinding> viewBindingsList = parseModelPathStrings(simpleBindings);
        return initModelPaths(viewBindingsList);
    }

    Expectations initModelPaths(boolean idCentric, String... simpleBindings) throws Exception {
        List<ViewBinding> viewBindingsList = parseModelPathStrings(idCentric, simpleBindings);
        return initModelPaths(viewBindingsList);
    }

    private ModelPath buildPath(String id, String path) {
        String[] pathMembers = toStringArray(path, "|");
        ModelPath.Builder builder = ModelPath.builder(id);
        int i = 0;
        for (String pathMember : pathMembers) {
            builder.addPathMember(toModelPathMember(i, pathMember.trim()));
            i++;
        }
        return builder.build();
    }

    private ModelPathStep toModelPathMember(int sortPrecedence, String pathMember) {

        try {
            String[] memberParts = toStringArray(pathMember, ".");
            if (pathMember.contains("." + ModelPathStepType.ref + ".") || pathMember.contains("." + ModelPathStepType.refs + ".")) {
                // Example: Content.ref_originalAuthor.ref.User
                Set<String> originClassName = splitClassNames(memberParts[0].trim());
                String refFieldName = memberParts[1].trim();
                ModelPathStepType stepType = ModelPathStepType.valueOf(memberParts[2].trim());
                Set<String> destinationClassName = splitClassNames(memberParts[3].trim());

                return new ModelPathStep(sortPrecedence == 0, originClassName,
                        refFieldName, stepType, destinationClassName, null);

            } else if (pathMember.contains("." + ModelPathStepType.backRefs + ".")
                    || pathMember.contains("." + ModelPathStepType.count + ".")
                    || pathMember.contains("." + ModelPathStepType.latest_backRef + ".")) {

                // Example: Content.backRefs.VersionedContent.ref_parent
                // Example: Content.count.VersionedContent.ref_parent
                // Example: Content.latest_backRef.VersionedContent.ref_parent
                Set<String> destinationClassName = splitClassNames(memberParts[0].trim());
                ModelPathStepType stepType = ModelPathStepType.valueOf(memberParts[1].trim());
                Set<String> originClassName = splitClassNames(memberParts[2].trim());
                String refFieldName = memberParts[3].trim();

                return new ModelPathStep(sortPrecedence == 0, originClassName,
                        refFieldName, stepType, destinationClassName, null);

            } else {

                // Example: User.firstName
                String[] valueFieldNames = toStringArray(memberParts[1], ",");
                for (int i = 0; i < valueFieldNames.length; i++) {
                    valueFieldNames[i] = valueFieldNames[i].trim();
                }
                Set<String> originClassName = splitClassNames(memberParts[0].trim());

                return new ModelPathStep(sortPrecedence == 0, originClassName,
                        null, ModelPathStepType.value, null, Arrays.asList(valueFieldNames));

            }
        } catch (Exception x) {
            throw new RuntimeException("fail to parse " + pathMember, x);
        }
    }

    private Set<String> splitClassNames(String classNames) {
        if (classNames.startsWith("[")) {
            classNames = classNames.replace("[", "");
            classNames = classNames.replace("]", "");

            return Sets.newHashSet(classNames.split("\\^"));
        } else {
            return Sets.newHashSet(classNames);
        }
    }

    OrderIdProvider idProvider() {
        return new OrderIdProvider() {
            private final AtomicLong id = new AtomicLong();

            @Override
            public long nextId() {
                return id.addAndGet(2); // Have to move by twos so there is room for add vs remove differentiation.
            }
        };
    }

//    UserIdentity whoami() {
//        return new UserIdentity(new Id(1L));
//    }
    JsonEventWriter jsonEventWriter(final TasmoViewMaterializer tasmoViewMaterializer, final OrderIdProvider idProvider) {
        return new JsonEventWriter() {
            JsonEventConventions jsonEventConventions = new JsonEventConventions();

            @Override
            public EventWriterResponse write(List<ObjectNode> events, EventWriterOptions options) throws JsonEventWriteException {
                try {
                    List<ObjectId> objectIds = Lists.newArrayList();
                    List<Long> eventIds = Lists.newArrayList();
                    for (ObjectNode w : events) {
                        long eventId = jsonEventConventions.getEventId(w);
                        if (eventId == 0) {
                            eventId = idProvider.nextId();
                            jsonEventConventions.setEventId(w, eventId);
                        }
                        eventIds.add(eventId);

                        String instanceClassname = jsonEventConventions.getInstanceClassName(w);
                        ObjectId objectId = new ObjectId(instanceClassname, jsonEventConventions.getInstanceId(w, instanceClassname));
                        objectIds.add(objectId);

                    }

                    List<WrittenEvent> writtenEvents = new ArrayList<>();
                    for (ObjectNode eventNode : events) {
                        writtenEvents.add(writtenEventProvider.convertEvent(eventNode));
                    }

                    tasmoViewMaterializer.process(writtenEvents);

                    return new EventWriterResponse(eventIds, objectIds);

                } catch (Exception ex) {
                    throw new JsonEventWriteException("sad trombone", ex);
                }
            }
        };
    }

    ObjectId write(Event event) throws EventWriteException {
        EventWriterResponse eventWriterResponse = writer.write(event);
        return eventWriterResponse.getObjectIds().get(0);
    }

    List<ObjectId> write(List<Event> events) throws EventWriteException {
        EventWriterResponse eventWriterResponse = writer.write(events);
        return eventWriterResponse.getObjectIds();
    }

    protected ObjectNode readView(TenantIdAndCentricId tenantIdAndCentricId, Id actorId, ObjectId viewId) throws IOException {
        ViewResponse viewResponse = viewProvider.readView(new ViewDescriptor(tenantIdAndCentricId, actorId, viewId));
        // kinda patch to refrain from refactoring dozens of tests... readView used to return null in case of a non-existing view
        return viewResponse.getStatusCode() == ViewResponse.StatusCode.OK ? viewResponse.getViewBody() : null;
    }

    protected ObjectNode readMaterializeView(TenantIdAndCentricId tenantIdAndCentricId, Id actorId, ObjectId viewId) throws IOException {
        ViewResponse viewResponse = readMaterializedViewProvider.readMaterializeView(new ViewDescriptor(tenantIdAndCentricId, actorId, viewId));
        // kinda patch to refrain from refactoring dozens of tests... readView used to return null in case of a non-existing view
        return viewResponse.getStatusCode() == ViewResponse.StatusCode.OK ? viewResponse.getViewBody() : null;
    }

    private String[] toStringArray(String string, String delim) {
        if (string == null || delim == null) {
            return new String[0];
        }
        StringTokenizer tokenizer = new StringTokenizer(string, delim);
        int tokenCount = tokenizer.countTokens();

        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = tokenizer.nextToken();
        }
        return tokens;
    }
}
