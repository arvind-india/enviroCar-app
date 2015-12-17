package org.envirocar.obd.adapter;

import android.test.InstrumentationTestCase;

import org.envirocar.obd.commands.PID;
import org.envirocar.obd.commands.PIDUtil;
import org.envirocar.obd.commands.request.BasicCommand;
import org.envirocar.obd.commands.request.PIDCommand;
import org.envirocar.obd.commands.request.elm.ConfigurationCommand;
import org.envirocar.obd.commands.response.DataResponse;
import org.envirocar.obd.commands.response.entity.MAFResponse;
import org.envirocar.obd.commands.response.entity.SpeedResponse;
import org.envirocar.obd.exception.AdapterFailedException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import rx.Subscription;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class SyncAdapterTest extends InstrumentationTestCase {

    @Test
    public void testInit() throws InterruptedException {
        MockAdapter adapter = new MockAdapter();

        ByteArrayInputStream bis = new ByteArrayInputStream("OK>OK>".getBytes());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        TestSubscriber<Void> testSubscriber = new TestSubscriber<>();

        Subscription sub = adapter.initialize(bis, bos)
                .observeOn(Schedulers.immediate())
                .subscribeOn(Schedulers.immediate())
                .subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
    }

    @Test
    public void testData() {
        MockAdapter adapter = new MockAdapter();

        ByteArrayInputStream bis = new ByteArrayInputStream("OK>OK>4110aabb>410daabb>".getBytes());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        TestSubscriber<Void> initSubscriber = new TestSubscriber<>();

        adapter.initialize(bis, bos)
                .subscribeOn(Schedulers.immediate())
                .observeOn(Schedulers.immediate())
                .subscribe(initSubscriber);

        initSubscriber.assertNoErrors();
        initSubscriber.assertCompleted();

        /**
         * now the actual data stuff
         */
        TestSubscriber<DataResponse> testSubscriber = new TestSubscriber<>();

        adapter.observe(Schedulers.immediate())
                .observeOn(Schedulers.immediate())
                .subscribeOn(Schedulers.immediate())
                .subscribe(testSubscriber);

        testSubscriber.assertNoErrors();

        List<DataResponse> received = testSubscriber.getOnNextEvents();

        Assert.assertThat(received.size(), CoreMatchers.is(2));

        Assert.assertThat(received.get(0), CoreMatchers.is(CoreMatchers.instanceOf(MAFResponse.class)));
        Assert.assertThat(received.get(1), CoreMatchers.is(CoreMatchers.instanceOf(SpeedResponse.class)));
    }

    private static class MockAdapter extends SyncAdapter {

        private final Queue<BasicCommand> initCommands;
        private final Queue<PIDCommand> dataCommands;
        private int metaResponse;

        public MockAdapter() {
            this.initCommands = new ArrayDeque<>();

            this.initCommands.offer(ConfigurationCommand.instance(ConfigurationCommand.Instance.ECHO_OFF));
            this.initCommands.offer(ConfigurationCommand.instance(ConfigurationCommand.Instance.HEADERS_OFF));

            this.dataCommands = new ArrayDeque<>();
            this.dataCommands.offer(PIDUtil.instantiateCommand(PID.MAF));
            this.dataCommands.offer(PIDUtil.instantiateCommand(PID.SPEED));
        }

        public int getMetaResponse() {
            return metaResponse;
        }

        @Override
        protected BasicCommand pollNextInitializationCommand() {
            return this.initCommands.poll();
        }

        @Override
        protected List<PIDCommand> providePendingCommands() {
            return new ArrayList<>(this.dataCommands);
        }

        @Override
        protected PIDCommand pollNextCommand() throws AdapterFailedException {
            return this.dataCommands.poll();
        }

        @Override
        protected boolean analyzeMetadataResponse(byte[] response, BasicCommand sentCommand) throws AdapterFailedException {
            return ++metaResponse >= 2;
        }

        @Override
        protected byte[] preProcess(byte[] bytes) {
            return bytes;
        }

        @Override
        public boolean supportsDevice(String deviceName) {
            return true;
        }
    }
}