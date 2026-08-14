/*
 * Time Temple - Kirston
 * Twilight of the Gods
 */

function start() {
    cm.sendAcceptDecline("如果我有善良的镜子，我就能重新召唤黑巫师！ \r\n等待！有点不对劲！为什么没有召唤黑巫师？等等，这是什么力量？我感觉到。。。完全不同于黑巫师啊！！！！！ \r\n\r\n #b(把手放在奇拉的肩膀上。)");
}

function action(mode, type, selection) {
    if (mode == 1) {
        cm.removeNpc(270050100, 2141000);
        cm.forceStartReactor(270050100, 2709000);
    }
    cm.dispose();

// If accepted, = summon PB + Kriston Disappear + 1 hour timer
// If deny = NoTHING HAPPEN
}