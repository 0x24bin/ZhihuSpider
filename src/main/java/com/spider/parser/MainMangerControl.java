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
import com.spider.tool.Config;
import com.spider.tool.Console;
import com.spider.tool.LruCacheImp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

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
    private SaveDaoInterface daoInterface;
    private int max = 500;
    private int max_active = Integer.valueOf(Config.INSTANCES().getThread_max_active());
    //parserfollwer
    private static final ThreadPoolExecutor servicePool = (ThreadPoolExecutor) Executors.
            newFixedThreadPool(Integer.valueOf(Config.INSTANCES().getThread_max_active()));
    //parserinfo
    private static final ThreadPoolExecutor servicePoolInfo = (ThreadPoolExecutor) Executors.
            newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public MainMangerControl() {
        userBases = new ArrayList();
        doneBaseUpdate = new ArrayList();
        //   tempUserBases = new LruCacheImp<>(350000);
        userInfo = new ArrayList();
        followNexuses = new ArrayList();
        daoInterface = new imp();
        token = new ArrayList<>(512);
    }

    public void star() {
        if (Config.INSTANCES().getIsOnlyParser().equals("false")) {
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

    private boolean isLoadTask = false;
    private long start = 0L;
    private long end = 0L;

    private void addTask() {
        if (isLoadTask)
            return;
        isLoadTask = true;
        new Thread(() -> {
            try {
                if (servicePool.getQueue().size() == 0 && servicePool.getActiveCount() < max_active) {
                    System.out.println("��������GetFollower");
                    synchronized (doneBaseUpdate) {
                        if (servicePool.getQueue().size() != 0) {
                            if (doneBaseUpdate.size() != 0) {
                                daoInterface.UpdateBase(doneBaseUpdate);
                                doneBaseUpdate.clear();
                            }
                            for (UserBase u : this.daoInterface.getNewForUserBase()) {
                                this.servicePool.execute(new ParserFollower(u, this));
                            }
                        }
                    }
                }
                if (servicePoolInfo.getQueue().size() == 0 && servicePoolInfo.getActiveCount() < max_active) {
                    synchronized (token) {
                        if (servicePoolInfo.getQueue().size() != 0) {
                            return;
                        }
                        System.out.println("��������ParserInfo");
                        if (token.size() != 0) {
                            daoInterface.UpdateParserInfo(token);
                        }
                        token = daoInterface.getParserInfoUserBase();
                        for (UserBase u : token) {
                            this.servicePoolInfo.execute(new ParserUserInfo(u, this));
                        }
                    }


                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLoadTask = false;
            }
        }).start();

    }

    public void remove(UserBase o) throws Exception {
        this.userBases.remove(o);
        doneBaseUpdate.add(o);
        if (this.doneBaseUpdate.size() > max) {
            daoInterface.UpdateBase(doneBaseUpdate);
            doneBaseUpdate.clear();
        }
    }

    private void addUserBase(List<UserBase> o) throws Exception {
        synchronized (o) {
            for (UserBase userBase : o) {
                if (!isExist(userBase)) {
                    this.userBases.add(userBase);
                }
            }
        }
        if (this.userBases.size() > max) {
            daoInterface.SaveForUserBase(userBases);
            this.userBases.clear();
        }

    }

    private void addUserInfo(UserInfo o) throws Exception {
        if (this.userInfo.size() > max) {
            synchronized (o){
            daoInterface.SaveForUser(userInfo);
            this.userInfo.clear();
            }
        }
        this.userInfo.add(o);
    }

    private void addUserFollower(List<FollowNexus> o) throws Exception {
        this.followNexuses.addAll(o);
        if (this.followNexuses.size() > max) {
            daoInterface.SaveForFollow(followNexuses);
            this.followNexuses.clear();

        }
    }

    /**
     * 1 userbase
     * 2 userinfo
     * 3 follower
     *
     * @param type
     * @param obj
     */
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
            try {
                while (true) {
                    addTask();
                    Console.clear();
                    System.out.println(
                            "��С�߳�����" + servicePool.getCorePoolSize() + "\n" +
                                    "����߳�����" + servicePool.getMaximumPoolSize() + "\n" +
                                    "������д�С��" + servicePool.getQueue().size() + "\n" +
                                    "����������" + servicePool.getTaskCount() + "\n" +
                                    "��Ծ�߳�����" + servicePool.getActiveCount() + "\n" +
                                    "����߳�����" + servicePool.getCompletedTaskCount() + "\n" +
                                    "�����С:" + tempUserBases.size() + "\n" +
                                    this.tempUserBases.toString() + "\n" +
                                    "========================================================\n" +
                                    "��С�߳�����" + servicePoolInfo.getCorePoolSize() + "\n" +
                                    "����߳�����" + servicePoolInfo.getMaximumPoolSize() + "\n" +
                                    "������д�С��" + servicePoolInfo.getQueue().size() + "\n" +
                                    "����������" + servicePoolInfo.getTaskCount() + "\n" +
                                    "��Ծ�߳�����" + servicePoolInfo.getActiveCount() + "\n" +
                                    "����߳�����" + servicePoolInfo.getCompletedTaskCount() + "\n"
                    );

                    System.out.println("����" + (System.currentTimeMillis() - time) / 1000.00 + "S");

                    Thread.sleep(3000);
                }
            } catch (Exception e) {

            }
        }).start();

    }
}