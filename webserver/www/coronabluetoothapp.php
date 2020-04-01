<?php
ini_set('display_errors', 0);

if (!isset($_REQUEST['me'])) {
    exit;
}

$me = $_REQUEST['me'];

if (!preg_match('/[a-z0-9-:]+/i', $me)) {
    exit;
}

$me = str_replace(':', '-', $me);

require_once dirname(__FILE__) . DIRECTORY_SEPARATOR . '..' . DIRECTORY_SEPARATOR . 'config.php';

$pdo = new PDO('mysql:host=' . $config['db_host'] . ';dbname=' . $config['db_database'], $config['db_username'], $config['db_password']);

$sql = 'CREATE TABLE IF NOT EXISTS devices (
    device VARCHAR(255),
    is_infected TINYINT(1) DEFAULT 0,
    datetime_infected DATETIME NULL DEFAULT NULL,
    datetime_lastcheck DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (device));';

$pdo->exec($sql);

$sql = 'INSERT INTO devices SET device=' . $pdo->quote($me);
$pdo->exec($sql);

if (isset($_REQUEST['isInfected'])) {
    $now = new DateTime('now', new DateTimeZone('UTC'));
    $sql = 'UPDATE devices SET is_infected=1, datetime_infected = ' . $pdo->quote($now->format('Y-m-d H:i:s')) . ' WHERE device=' . $pdo->quote($me);
    $pdo->exec($sql);
} else if (isset($_REQUEST['checkInfected'])) {
    $numNewInfections = 0;

    $sql = 'SELECT * FROM devices WHERE device=' . $pdo->quote($me);
    $res = $pdo->query($sql);
    if ($res) {
        $row = $res->fetch();

        $datetimeLastcheck = $row['datetime_lastcheck'];
        if (!$datetimeLastcheck) {
            $datetimeLastcheck = '0000-00-00 00:00:00';
        }

        $sql = 'SELECT device FROM devices WHERE
            device != ' . $pdo->quote($me) . '
            AND is_infected = 1
            AND datetime_infected > ' . $pdo->quote($datetimeLastcheck);

        $res = $pdo->query($sql);

        $now = new DateTime('now', new DateTimeZone('UTC'));
        $datetimeLastcheck = $now->format('Y-m-d H:i:s');
        $sql = 'UPDATE devices SET datetime_lastcheck=' . $pdo->quote($datetimeLastcheck) . ' WHERE device=' . $pdo->quote($me);
        $pdo->exec($sql);

        if($res) {
            $ret = array();
            while ($row = $res->fetch()) {
                $ret[] = $row['device'];
            }
            print json_encode(array('devices' => $ret));
            exit;
        }
    }
    exit;
}
print 1;