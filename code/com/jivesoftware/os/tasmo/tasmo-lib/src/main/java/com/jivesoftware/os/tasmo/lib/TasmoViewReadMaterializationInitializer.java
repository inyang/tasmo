package com.jivesoftware.os.tasmo.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.jivesoftware.os.jive.utils.logger.MetricLogger;
import com.jivesoftware.os.jive.utils.logger.MetricLoggerFactory;
import com.jivesoftware.os.tasmo.lib.model.TasmoViewModel;
import com.jivesoftware.os.tasmo.lib.read.ReadMaterializedViewProvider;
import com.jivesoftware.os.tasmo.lib.read.ReadMaterializer;
import com.jivesoftware.os.tasmo.lib.write.CommitChange;
import com.jivesoftware.os.tasmo.view.reader.api.ViewReadMaterializer;
import com.jivesoftware.os.tasmo.view.reader.api.ViewResponse;
import com.jivesoftware.os.tasmo.view.reader.service.JsonViewMerger;
import com.jivesoftware.os.tasmo.view.reader.service.ViewAsObjectNode;
import com.jivesoftware.os.tasmo.view.reader.service.ViewPermissionChecker;
import org.merlin.config.Config;

/**
 *
 *
 */
public class TasmoViewReadMaterializationInitializer {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    public static interface TasmoViewReadMaterializationConfig extends Config {
    }

    public static TasmoServiceHandle<ViewReadMaterializer<ViewResponse>> initialize(TasmoViewReadMaterializationConfig config,
        TasmoViewModel tasmoViewModel,
        ReadMaterializer readMaterializer,
        ViewPermissionChecker viewPermissionChecker,
        Optional<CommitChange> commitChangeVistor) throws Exception {


        // TODO add config option to switch between batching and serial.
        ViewAsObjectNode viewAsObjectNode = new ViewAsObjectNode();

        final ViewReadMaterializer<ViewResponse> viewReadMaterializer = new ReadMaterializedViewProvider<>(viewPermissionChecker,
            readMaterializer,
            viewAsObjectNode,
            new JsonViewMerger(new ObjectMapper()),
            commitChangeVistor,
            1_024 * 1_024 * 10);

        return new TasmoServiceHandle<ViewReadMaterializer<ViewResponse>>() {

            @Override
            public ViewReadMaterializer<ViewResponse> getService() {
                return viewReadMaterializer;
            }

            @Override
            public void start() throws Exception {
            }

            @Override
            public void stop() throws Exception {
            }
        };
    }

}
