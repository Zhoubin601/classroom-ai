"""Live API checks. Creates only uniquely named synthetic records and removes them."""
import json
import os
import unittest
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('CLASSROOM_API_URL', 'http://127.0.0.1:8080')


def request(route, method='GET', payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(BASE + route, data=data, method=method, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


class LiveApiTests(unittest.TestCase):
    def test_dashboard_and_business_read_routes(self):
        routes = ['/api/v1/courses', '/api/v1/courses/offerings', '/api/v1/schedules',
                  '/api/v1/resources', '/api/v1/supervisions', '/api/v1/supervisions/analytics/dashboard',
                  '/api/v1/supervisions/analytics/alerts', '/api/v1/attendance/current',
                  '/api/student/list', '/api/visual/overview', '/api/visual/trend', '/api/visual/students/status']
        for route in routes:
            with self.subTest(route=route):
                status, body = request(route)
                self.assertEqual(status, 200)
                self.assertEqual(body['code'], 200)

    def test_existing_indicator_serialization(self):
        _, courses = request('/api/v1/courses')
        for course in courses['data']:
            with self.subTest(course=course['id']):
                status, body = request(f"/api/v1/syllabus/course/{course['id']}/indicators")
                self.assertEqual(status, 200)
                self.assertIsInstance(body['data'], list)

    def test_synthetic_course_and_resource_roundtrip(self):
        code = 'TEST_' + uuid.uuid4().hex[:12]
        course_id = resource_id = None
        try:
            status, body = request('/api/v1/courses', 'POST', {
                'courseCode': code, 'courseName': 'Automated regression fixture', 'credits': 1.0,
                'hours': 16, 'department': 'TEST', 'courseType': 'TEST'})
            self.assertEqual(status, 200)
            course_id = body['data']['id']
            self.assertEqual(request(f'/api/v1/courses/{course_id}')[1]['data']['courseCode'], code)
            status, body = request('/api/v1/resources', 'POST', {
                'courseId': course_id, 'chapter': 'Test chapter', 'resourceName': 'Test metadata only', 'fileSizeBytes': 1,
                'fileUrl': '/test-fixture.pdf', 'fileType': 'PDF'})
            self.assertEqual(body['code'], 200)
            resource_id = body['data']['id']
            self.assertEqual(request(f'/api/v1/resources/{resource_id}')[1]['data']['fileSizeBytes'], 1)
        finally:
            if resource_id is not None:
                self.assertEqual(request(f'/api/v1/resources/{resource_id}', 'DELETE')[1]['code'], 200)
            if course_id is not None:
                self.assertEqual(request(f'/api/v1/courses/{course_id}', 'DELETE')[1]['code'], 200)

    def test_synthetic_face_roundtrip(self):
        student_id = 'TEST_' + uuid.uuid4().hex[:12]
        vector = [1.0] + [0.0] * 511
        try:
            status, body = request('/api/face/register', 'POST', {
                'studentId': student_id, 'name': 'Synthetic Test', 'className': 'TEST',
                'gender': 'UNKNOWN', 'featureVector': vector})
            self.assertEqual(body['code'], 200)
            status, match = request('/api/face/search', 'POST', {'featureVector': vector, 'threshold': 0.99999})
            self.assertTrue(match['data']['matched'])
            self.assertEqual(match['data']['studentId'], student_id)
        finally:
            self.assertEqual(request('/api/student/' + student_id, 'DELETE')[1]['code'], 200)

    def test_invalid_inputs_are_client_errors(self):
        cases = [('/api/visual/report/stream', {'lookupRate': 90}),
                 ('/api/v1/schedules', {'classroom': 'TEST', 'dayOfWeek': 8, 'startPeriod': 1, 'endPeriod': 2}),
                 ('/api/face/search', {'featureVector': [1]})]
        for route, payload in cases:
            with self.subTest(route=route):
                status, body = request(route, 'POST', payload)
                self.assertTrue(status == 400 or body['code'] == 400)


if __name__ == '__main__':
    unittest.main(verbosity=2)
