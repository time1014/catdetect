const express = require('express');
const mysql = require('mysql2');
const multer = require('multer');
const path = require('path');
const cors = require('cors');
const fs = require('fs');
const app = express();

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use('/uploads', express.static(path.join(__dirname, 'uploads'))); // 이미지 접근 경로

// MySQL 연결
const db = mysql.createConnection({
  host: 'localhost',
  user: 'root',
  password: '?', // 비밀번호를 실제로 입력해야 합니다.
  database: '?' //데이터베이스 테이블 이름
});
db.connect(err => {
  if (err) return console.error('MySQL 연결 실패:', err);
  console.log('MySQL 연결 성공!');
});

// DB에서 전체 데이터 불러오기
app.get('/api/data', (req, res) => {
    const query = 'SELECT * FROM cat';
  
    db.query(query, (err, results) => {
      if (err) {
        console.error('쿼리 에러:', err);
        res.status(500).json({ error: 'DB 쿼리 실패' });
        return;
      }
      
      // 시간 포맷 변환 함수 (time: HH:mm:ss → 오전/오후 h시 m분 s초)
      function convertToKoreanTimeFormat(timeStr) {
        const [hourStr, minuteStr, secondStr] = timeStr.split(':');
        let hour = parseInt(hourStr);
        const minute = parseInt(minuteStr);
        const second = parseInt(secondStr);
  
        const period = hour < 12 ? '오전' : '오후';
        if (hour > 12) hour -= 12;
        if (hour === 0) hour = 12;
  
        return `${period} ${hour}시 ${minute}분 ${second}초`;
      }
  
      // 날짜 포맷 변환 함수 (UTC ISO → KST → YYYY년 M월 D일 H시 M분 S초)
      function convertDateToKorean(dateStr) {
        const date = new Date(dateStr);
        // UTC -> KST (+9시간)
        const kstDate = new Date(date.getTime() + (9 * 60 * 60 * 1000));
  
        const year = kstDate.getFullYear();
        const month = kstDate.getMonth() + 1;
        const day = kstDate.getDate();
  
        return `${year}년 ${month}월 ${day}일`;
      }
  
      // 결과 변환
      const convertedResults = results.map(row => {
        return {
          ...row,
          time: convertToKoreanTimeFormat(row.time),
          date: convertDateToKorean(row.date)
        };
      });
  
      res.json(convertedResults);
    });
  });

// 파일 저장 설정
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadPath = 'uploads/';
    if (!fs.existsSync(uploadPath)) fs.mkdirSync(uploadPath);
    cb(null, uploadPath);
  },
  filename: (req, file, cb) => {
    const uniqueName = Date.now() + '-' + Math.random().toString(36).substring(7) + path.extname(file.originalname);
    cb(null, uniqueName);
  }
});
const upload = multer({ storage });



app.post('/api/detect', upload.single('snapshot'), (req, res) => {
  const { date, time } = req.body;
  const snapshotPath = req.file ? req.file.path : null;

  if (!snapshotPath) {
    return res.status(400).json({ error: '스냅샷 누락' });
  }

  const utcDateTime = new Date(`${date}T${time}Z`);
  const kstTimestamp = new Date(utcDateTime.getTime() + 9 * 60 * 60 * 1000);
  const kstDate = kstTimestamp.toISOString().slice(0, 10);
  const hours = String(kstTimestamp.getUTCHours()).padStart(2, '0');
  const minutes = String(kstTimestamp.getUTCMinutes()).padStart(2, '0');
  const seconds = String(kstTimestamp.getUTCSeconds()).padStart(2, '0');
  const kstTime = `${hours}:${minutes}:${seconds}`;

  console.log('UTC:', utcDateTime.toISOString(), '| KST:', kstDate, kstTime);

  const insertQuery = 'INSERT INTO cat (date, time, snapshot) VALUES (?, ?, ?)';
  db.query(insertQuery, [kstDate, kstTime, snapshotPath], (err, result) => {
    if (err) return res.status(500).json({ error: '삽입 실패' });
    const insertedId = result.insertId;
    res.status(200).json({ id: insertedId, message: '감지 기록 저장됨' });
  });
});

// 고양이 계속 감지 확인 API
app.post('/api/verify-detection', upload.none(), (req, res) => {
  console.log('요청 바디:', req.body); // 이제 잘 나올 것
  const { id } = req.body || {};
  if (!id) return res.status(400).json({ error: 'id 누락' });

  deleteDetectionFromDatabase(id);
  res.status(200).json({ message: `ID ${id}의 데이터가 삭제되었습니다.` });
});

// DB에서 삭제 + 이미지 파일도 삭제
function deleteDetectionFromDatabase(detectionId) {
  const selectQuery = 'SELECT snapshot FROM cat WHERE id = ?';
  db.query(selectQuery, [detectionId], (selectErr, results) => {
    if (selectErr || results.length === 0) {
      console.error('데이터 조회 실패:', selectErr); // null로 실패 뜨는데 딱히 상관 없는듯
      return;
    }

    const filePath = results[0].snapshot;
    const deleteQuery = 'DELETE FROM cat WHERE id = ?';
    db.query(deleteQuery, [detectionId], (deleteErr) => {
      if (deleteErr) {
        console.error('데이터 삭제 실패:', deleteErr);
        return;
      }

      fs.unlink(filePath, (fsErr) => {
        if (fsErr) {
          console.error('이미지 파일 삭제 실패:', fsErr);
        } else {
          console.log(`이미지 ${filePath} 삭제됨`);
        }
      });
    });
  });
}

const moment = require('moment'); // npm install moment

app.get('/api/weekly-counts', (req, res) => {
  const year = parseInt(req.query.year);
  const week = parseInt(req.query.week);

  // 주어진 연도와 주차의 일요일 ~ 토요일 구하기
  const startOfWeek = moment().utcOffset(9).year(year).week(week).startOf('week'); // 일요일
  const endOfWeek = moment().utcOffset(9).year(year).week(week).endOf('week'); // 토요일

  // 날짜 포맷을 YYYY-MM-DD로 변환
  const startOfWeekFormatted = startOfWeek.format('YYYY-MM-DD');
  const endOfWeekFormatted = endOfWeek.format('YYYY-MM-DD');

  const query = `
    SELECT DATE(date) as date, SUM(count) AS total_count
    FROM cat
    WHERE DATE(date) BETWEEN ? AND ?
    GROUP BY DATE(date)
    ORDER BY DATE(date)
  `;

  db.query(query, [startOfWeekFormatted, endOfWeekFormatted], (err, results) => {
    if (err) {
      console.error('쿼리 에러:', err);
      res.status(500).json({ error: 'DB 쿼리 실패' });
      return;
    }

    // 결과에서 날짜를 KST로 변환하여 반환
    const correctedResults = results.map(result => {
      const correctedDate = moment(result.date).utcOffset(9).format('YYYY-MM-DD'); // KST로 변환
      result.date = correctedDate;
      return result;
    });

    res.json(correctedResults); // 예: [{ date: '2025-04-27', total_count: 2 }, ...]
  });
});


app.get('/api/daily-report', (req, res) => {
  const dateParam = req.query.date;
  const query = `
    SELECT id, date, time, count, snapshot
    FROM cat
    WHERE DATE(date) = ?
    ORDER BY time ASC
  `;

  db.query(query, [dateParam], (err, results) => {
    if (err) {
      console.error('쿼리 실패:', err);
      return res.status(500).json({ error: 'DB 오류' });
    }

    // snapshot 경로를 전체 URL로 바꿔서 응답
    const updatedResults = results.map(row => {
      return {
        ...row,
        snapshot: `http://10.0.2.2:3000/${row.snapshot.replace(/\\/g, '/')}`  // 백슬래시 → 슬래시
      };
    });

    res.json(updatedResults);
  });
});

app.get('/api/yesterday-report', (req, res) => {
  // 어제 날짜 계산 (KST 기준)
  const yesterday = new Date();
  
  // 한국시간 (KST)으로 변환
  const kstOffset = 9 * 60;  // 한국시간은 UTC보다 9시간 빠름 (9시간 * 60분)
  const utcTime = yesterday.getTime() + (yesterday.getTimezoneOffset() * 60000);
  const kstTime = new Date(utcTime + (kstOffset * 60000));

  // 어제 날짜 계산
  kstTime.setDate(kstTime.getDate() - 1);
  const year = kstTime.getFullYear();
  const month = String(kstTime.getMonth() + 1).padStart(2, '0'); // 1월은 0부터 시작
  const day = String(kstTime.getDate()).padStart(2, '0');
  const dateParam = `${year}-${month}-${day}`;

  const query = `
    SELECT id, date, time, count, snapshot
    FROM cat
    WHERE DATE(date) = ?
    ORDER BY time ASC
  `;

  db.query(query, [dateParam], (err, results) => {
    if (err) {
      console.error('쿼리 실패:', err);
      return res.status(500).json({ error: 'DB 오류' });
    }

    // snapshot 경로를 전체 URL로 바꿔서 응답
    const updatedResults = results.map(row => {
      return {
        ...row,
        snapshot: `http://10.0.2.2:3000/${row.snapshot.replace(/\\/g, '/')}`  // 백슬래시 → 슬래시
      };
    });

    // 어제 날짜의 고양이 섭취 기록 개수
    const count = updatedResults.length;

    res.json({ count, records: updatedResults });
  });
});




const PORT = 3000;
app.listen(PORT, () => {
  console.log(`서버 실행 중: http://localhost:${PORT}`);
});
