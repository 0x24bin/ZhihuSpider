package com.spider.parser;
//=======================================================
//		          .----.
//		       _.'__    `.
//		   .--(^)(^^)---/!\
//		 .' @          /!!!\
//		 :         ,    !!!!
//		  `-..__.-' _.-\!!!/
//		        `;_:    `"'
//		      .'"""""`.
//		     /,  ya ,\\
//		    //������\\
//		    `-._______.-'
//		    ___`. | .'___
//		   (______|______)
//=======================================================


import com.spider.dao.SaveDaoInterface;
import com.spider.dao.imp;
import com.spider.entity.FollowNexus;
import com.spider.entity.UserBase;
import com.spider.entity.UserInfo;
import com.spider.https.ZhiHuHttp;
import com.spider.tool.Config;
import com.spider.tool.Console;
import com.spider.tool.LruCacheImp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [Zhihu]https://www.zhihu.com/people/Sweets07
 * [Github]https://github.com/MatrixSeven
 * Created by seven on 2016/12/2.
 */
public class MainMangerControl {
    private long time = System.currentTimeMillis();
    private volatile List<UserBase> userBases;
    private volatile List<UserBase> doneBaseUpdate;
    private volatile List<UserInfo> userInfo;
    private volatile List<UserBase> token;
    private volatile LruCacheImp<UserBase> tempUserBases;
    private volatile List<FollowNexus> followNexuses;
    private volatile AtomicLong atomicLong = new AtomicLong(0L);
    private volatile boolean isLoadTask_ = false;
    private volatile boolean isLoadTask__ = false;
    private volatile long time_ = 0L;
    private SaveDaoInterface daoInterface;
    private int max = 500;
    private boolean isOnlyParser=Config.INSTANCES().getIsOnlyParser();
    private int max_active = Integer.valueOf(Config.INSTANCES().getThread_max_active());
    private static final ThreadPoolExecutor servicePool = (ThreadPoolExecutor) Executors.
            newFixedThreadPool(Integer.valueOf(Config.INSTANCES().getThread_max_active()));
    private static final ThreadPoolExecutor servicePoolInfo = (ThreadPoolExecutor) Executors.
            newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public MainMangerControl() throws Exception{
        ZhiHuHttp.login();
        userBases = new ArrayList();
        doneBaseUpdate = new ArrayList();
        userInfo = new ArrayList();
        followNexuses = new ArrayList();
        daoInterface = new imp();
        token = new ArrayList<>(512);
    }

    public void star() {
        if (!isOnlyParser){
            try {
                tempUserBases = daoInterface.iniTemp(350000);
                for (UserBase u : daoInterface.Init(new UserBase(Config.INSTANCES().getStar_token())))
                    this.servicePool.execute(new ParserFollower(u, this));

                printStatus();

            } catch (Exception e) {
                e.printStackTrace();

            }
        }
    }

    public boolean isExist(UserBase userBase) throws Exception {
        if (tempUserBases.containsKey(userBase) || daoInterface.isExist(userBase)) {
            return true;
        }
        return false;
    }


    private void addTask() {
        try {
            if (servicePool.getQueue().size() == 0 && servicePool.getActiveCount() < max_active&&!isOnlyParser) {
                System.out.println("��������GetFollower");
                if (!isLoadTask_) {
                    synchronized (doneBaseUpdate) {
                        if (servicePool.getQueue().size() == 0) {
                            isLoadTask_ = true;
                            if (doneBaseUpdate.size() != 0) {
                                daoInterface.UpdateBase(doneBaseUpdate);
                                doneBaseUpdate.clear();
                            }
                            for (UserBase u : this.daoInterface.getNewForUserBase()) {
                                this.servicePool.execute(new ParserFollower(u, this));
                            }
                        }
                        isLoadTask_ = false;
                    }
                }
            }
            if (servicePoolInfo.getQueue().size() == 0 && servicePoolInfo.getActiveCount() <= 4) {
                if (!isLoadTask__) {
                    synchronized (token) {
                        if (servicePoolInfo.getQueue().size() != 0) {
                            return;
                        }
                        isLoadTask__ = true;
                        System.out.println("��������ParserInfo");
                        if (token.size() != 0) {
                            time_ = daoInterface.UpdateParserInfo(token);
                        }
                        token = daoInterface.getParserInfoUserBase();
                        for (UserBase u : token) {
                            this.servicePoolInfo.execute(new ParserUserInfo(u, this));
                        }
                        isLoadTask__ = false;
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }

    }

    public void remove(UserBase o) throws Exception {
        //�������
        userBases.remove(o);
        doneBaseUpdate.add(o);
        //����base
        if (this.doneBaseUpdate.size() > max) {
            daoInterface.UpdateBase(doneBaseUpdate);
            doneBaseUpdate.clear();
        }
    }



    private void addUserBase(List<UserBase> o) throws Exception {
        if (this.userBases.size() > max ||
                (servicePool.getQueue().size() == 0 && servicePool.getActiveCount() < max_active && doneBaseUpdate.size() > 0)) {
            synchronized (userBases) {
                daoInterface.SaveForUserBase(userBases);
                this.userBases.clear();
            }
        }
        for (UserBase userBase : o) {
            if (!isExist(userBase)) {
                this.userBases.add(userBase);
            }
            atomicLong.incrementAndGet();
        }
    }
    private void addUserInfo(UserInfo o) throws Exception {
        if (this.userInfo.size() > max) {
            synchronized (o) {
                daoInterface.SaveForUser(userInfo);
                this.userInfo.clear();
            }
        }
        this.userInfo.add(o);

    }
    private void addUserFollower(List<FollowNexus> o) throws Exception {
        this.followNexuses.addAll(o);
        if (this.followNexuses.size() > max) {
            synchronized (followNexuses) {
                daoInterface.SaveForFollow(followNexuses);
                this.followNexuses.clear();
            }
        }
    }
    public void addType(Integer type, Object obj) throws Exception {
        switch (type) {
            case 1:
                this.addUserBase((List<UserBase>) obj);
                break;
            case 2:
                this.addUserInfo((UserInfo) obj);
                break;
            case 3:
                this.addUserFollower((List<FollowNexus>) obj);
                break;
            default:
                break;
        }
    }


    public void printStatus() {
        new Thread(() -> {
            while (true) {
                addTask();
                try {
                    Thread.sleep(10000);
                } catch (Exception e) {

                }
            }
        }).start();
        new Thread(() -> {
            try {
                while (true) {
                    Console.clear();
                    System.out.println(
                            "��С�߳�����" + servicePool.getCorePoolSize() + "\n" +
                                    "����߳�����" + servicePool.getMaximumPoolSize() + "\n" +
                                    "������д�С��" + servicePool.getQueue().size() + "\n" +
                                    "����������" + servicePool.getTaskCount() + "\n" +
                                    "��Ծ�߳�����" + servicePool.getActiveCount() + "\n" +
                                    "����߳�����" + servicePool.getCompletedTaskCount() + "\n" +
                                    "�����⻹��: " + (max - userBases.size()) + "\n" +
                                    "�����ظ�����: " + atomicLong.intValue() + "\n" +
                                    "�����С:" + tempUserBases.size() + "\n" +
                                    this.tempUserBases.toString() + "\n" +
                                    "========================================================\n" +
                                    "��С�߳�����" + servicePoolInfo.getCorePoolSize() + "\n" +
                                    "����߳�����" + servicePoolInfo.getMaximumPoolSize() + "\n" +
                                    "������д�С��" + servicePoolInfo.getQueue().size() + "\n" +
                                    "����������" + servicePoolInfo.getTaskCount() + "\n" +
                                    "��Ծ�߳�����" + servicePoolInfo.getActiveCount() + "\n" +
                                    "����߳�����" + servicePoolInfo.getCompletedTaskCount() + "\n" +
                                    "���»���ʱ��:" + time_ + "s" + "\n"
                    );

                    System.out.println("����" + (System.currentTimeMillis() - time) / 1000.00 + "S");

                    Thread.sleep(3000);
                }
            } catch (Exception e) {

            }
        }).start();

    }
}