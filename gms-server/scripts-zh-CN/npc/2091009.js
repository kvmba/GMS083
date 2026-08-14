var status;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1 || (mode == 0 && status == 0)) {
        cm.dispose();
        return;
    } else if (mode == 0) {
        status--;
    } else {
        status++;
    }


    if (status == 0) {
        cm.sendGetText("The entrance of the Sealed Shrine... #bPassword#k!");
    } else if (status == 1) {
        if (cm.getWarpMap(925040100).countPlayers() > 0) {
            cm.sendOk("有人已经在前往封印神殿的路上了。");
            cm.dispose();
            return;
        }
        if (cm.getText() == "Actions speak louder than words") {
            if (cm.isQuestStarted(21747) && cm.getQuestProgressInt(21747, 9300351) == 0) {
                cm.warp(925040100, 0);
            } else {
                cm.playerMessage(5, "尽管你说出了正确答案，但某种神秘的力量挡住了道路。");
            }

            cm.dispose();
        } else {
            cm.sendOk("#r错误！");
        }
    } else if (status == 2) {
        cm.dispose();
    }
}